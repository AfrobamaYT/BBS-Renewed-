package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Map;

/** Bone and rig-property preconditions for conditional actor writes. */
final class ActorChannelPatchComplexValidation
{
    private ActorChannelPatchComplexValidation()
    {
    }

    static Result check(String uuid, JsonObject patch, JsonObject expected, JsonObject current)
    {
        JsonObject properties = objectOrEmpty(current.get(ActorProtocol.REPLAY_PROPERTIES));

        Result rig = checkRigProperties(uuid, patch, expected, properties);

        if (!rig.error.isEmpty())
        {
            return rig;
        }

        Result bones = checkBones(uuid, patch, expected, properties);

        if (!bones.error.isEmpty())
        {
            return bones;
        }

        Result grid = ShapeGridPatch.check(uuid, patch, expected, properties);

        if (!grid.error.isEmpty())
        {
            return grid;
        }
        if (grid.seen && (patch.has(ShapeKeysPatch.SECTION)
            || patch.has(ShapeTimelinePatch.SECTION)))
        {
            return Result.error("shapeGrid cannot be mixed with partial ShapeKey patches",
                uuid + "/" + ShapeGridPatch.SECTION);
        }

        Result visibility = VisibilityPatch.check(uuid, patch, expected, current);

        if (!visibility.error.isEmpty())
        {
            return visibility;
        }

        Result shapes = checkShapeKeys(uuid, patch, expected, properties);

        if (!shapes.error.isEmpty())
        {
            return shapes;
        }

        Result timeline = ShapeTimelinePatch.check(uuid, patch, expected, properties);

        if (!timeline.error.isEmpty())
        {
            return timeline;
        }

        return Result.ok(
            rig.seen || bones.seen || grid.seen || visibility.seen
                || shapes.seen || timeline.seen,
            rig.needsWrite || bones.needsWrite || grid.needsWrite
                || visibility.needsWrite || shapes.needsWrite || timeline.needsWrite);
    }

    private static Result checkRigProperties(String uuid, JsonObject patch, JsonObject expected,
        JsonObject properties)
    {
        JsonElement desiredRaw = patch.get(RigPropertyCurves.KEY);

        if (desiredRaw == null)
        {
            return Result.empty();
        }
        if (!desiredRaw.isJsonObject())
        {
            return Result.error("rigProperties must be an object", uuid + "/rigProperties");
        }

        JsonObject expectedRig = requiredObject(expected, RigPropertyCurves.KEY);

        if (expectedRig == null)
        {
            return Result.error("missing rig-property preconditions", uuid + "/rigProperties");
        }

        JsonObject currentRig = objectOrEmpty(properties.get(RigPropertyCurves.KEY));
        boolean seen = false;
        boolean needsWrite = false;

        for (Map.Entry<String, JsonElement> boneEntry : desiredRaw.getAsJsonObject().entrySet())
        {
            String bone = boneEntry.getKey();

            if (bone.isEmpty() || bone.length() > 256 || !boneEntry.getValue().isJsonObject())
            {
                return Result.error("invalid rig-property bone", uuid + "/rigProperties/" + bone);
            }

            JsonObject expectedBone = objectOrNull(expectedRig.get(bone));
            JsonObject currentBone = objectOrEmpty(currentRig.get(bone));

            if (expectedBone == null)
            {
                return Result.error("missing rig-property bone preconditions",
                    uuid + "/rigProperties/" + bone);
            }

            for (Map.Entry<String, JsonElement> propEntry
                : boneEntry.getValue().getAsJsonObject().entrySet())
            {
                String prop = propEntry.getKey();
                String path = uuid + "/rigProperties/" + bone + "/" + prop;

                if (prop.isEmpty() || prop.length() > 256)
                {
                    return Result.error("invalid rig-property name", path);
                }
                String invalid = RigPropertyCurves.validate(propEntry.getValue());
                if (!invalid.isEmpty())
                {
                    return Result.error(invalid, path);
                }
                if (!expectedBone.has(prop))
                {
                    return Result.error("missing rig-property precondition", path);
                }

                JsonElement currentValue = currentBone.has(prop)
                    ? currentBone.get(prop) : JsonNull.INSTANCE;
                JsonElement desiredValue = RigPropertyCurves.canonical(propEntry.getValue());
                JsonElement expectedValue = RigPropertyCurves.canonical(expectedBone.get(prop));
                JsonElement canonicalCurrent = RigPropertyCurves.canonical(currentValue);

                seen = true;
                if (canonicalCurrent.equals(desiredValue))
                {
                    continue;
                }
                if (!canonicalCurrent.equals(expectedValue))
                {
                    return Result.error("precondition failed", path);
                }
                needsWrite = true;
            }
        }

        return Result.ok(seen, needsWrite);
    }

    private static Result checkShapeKeys(String uuid, JsonObject patch, JsonObject expected,
        JsonObject properties)
    {
        JsonElement desiredRaw = patch.get(ShapeKeysPatch.SECTION);

        if (desiredRaw == null)
        {
            return Result.empty();
        }
        if (!desiredRaw.isJsonObject())
        {
            return Result.error("shapeKeys must be an object", uuid + "/shapeKeys");
        }

        JsonObject expectedShapes = requiredObject(expected, ShapeKeysPatch.SECTION);

        if (expectedShapes == null)
        {
            return Result.error("missing ShapeKey preconditions", uuid + "/shapeKeys");
        }

        JsonObject desired = desiredRaw.getAsJsonObject();
        JsonObject currentShapes = objectOrEmpty(properties.get(ShapeKeysPatch.SECTION));
        boolean needsWrite = false;
        JsonArray proposedFirstGrid = null;

        for (Map.Entry<String, JsonElement> entry : desired.entrySet())
        {
            String name = entry.getKey();
            String path = uuid + "/shapeKeys/" + name;

            if (name.isEmpty() || name.length() > 256)
            {
                return Result.error("invalid ShapeKey name", path);
            }
            if (!expectedShapes.has(name))
            {
                return Result.error("missing ShapeKey precondition", path);
            }

            JsonElement desiredValue = entry.getValue();
            JsonElement expectedValue = expectedShapes.get(name);
            String invalid = ShapeKeysPatch.validateDesired(
                desiredValue, expectedValue, currentShapes);

            if (!invalid.isEmpty())
            {
                return Result.error(invalid, path);
            }
            if (currentShapes.size() == 0 && expectedValue.isJsonNull()
                && desiredValue != null && desiredValue.isJsonArray())
            {
                JsonArray candidate = desiredValue.getAsJsonArray();

                if (proposedFirstGrid == null)
                {
                    proposedFirstGrid = candidate;
                }
                else if (!ShapeKeysPatch.sameGrid(candidate, proposedFirstGrid))
                {
                    return Result.error(
                        "first ShapeKey morphs must share one key grid", path);
                }
            }

            JsonElement currentValue = currentShapes.has(name)
                ? currentShapes.get(name) : JsonNull.INSTANCE;
            JsonElement canonicalCurrent = ActorChannelPatchCanonical.value(currentValue);
            JsonElement canonicalDesired = ActorChannelPatchCanonical.value(desiredValue);
            JsonElement canonicalExpected = ActorChannelPatchCanonical.value(expectedValue);

            if (canonicalCurrent.equals(canonicalDesired))
            {
                continue;
            }
            if (!canonicalCurrent.equals(canonicalExpected))
            {
                return Result.error("precondition failed", path);
            }

            needsWrite = true;
        }

        return Result.ok(!desired.entrySet().isEmpty(), needsWrite);
    }

    private static Result checkBones(String uuid, JsonObject patch, JsonObject expected,
        JsonObject properties)
    {
        JsonElement desiredRaw = patch.get("bones");

        if (desiredRaw == null)
        {
            return Result.empty();
        }
        if (!desiredRaw.isJsonObject())
        {
            return Result.error("bones must be an object", uuid + "/bones");
        }

        JsonObject desired = desiredRaw.getAsJsonObject();
        JsonObject expectedBones = requiredObject(expected, "bones");
        JsonObject expectedPose = requiredObject(expected, "poseTrack");

        if (expectedBones == null || expectedPose == null)
        {
            return Result.error("missing bone/pose preconditions", uuid + "/bones");
        }

        JsonObject currentBones = objectOrEmpty(properties.get("bones"));
        JsonObject currentPose = objectOrEmpty(properties.get("poseTrack"));
        boolean needsWrite = false;

        for (Map.Entry<String, JsonElement> entry : desired.entrySet())
        {
            String bone = entry.getKey();
            String path = uuid + "/bones/" + bone;
            String invalid = ActorChannelPatchStructuredValues.validateBone(entry.getValue());

            if (bone.isEmpty() || !invalid.isEmpty())
            {
                return Result.error(invalid.isEmpty() ? "bone name is empty" : invalid, path);
            }
            if (!expectedBones.has(bone) || !expectedPose.has(bone))
            {
                return Result.error("missing bone precondition", path);
            }

            JsonElement currentBone = currentBones.has(bone) ? currentBones.get(bone) : JsonNull.INSTANCE;
            JsonElement currentPoseBone = currentPose.has(bone) ? currentPose.get(bone) : JsonNull.INSTANCE;
            JsonElement desiredBone = entry.getValue();
            JsonElement canonicalCurrent = ActorChannelPatchCanonical.bone(currentBone);
            JsonElement canonicalDesired = ActorChannelPatchCanonical.bone(desiredBone);
            JsonElement canonicalExpected = ActorChannelPatchCanonical.bone(expectedBones.get(bone));

            if (canonicalCurrent.equals(canonicalDesired) && currentPoseBone.isJsonNull())
            {
                continue;
            }
            if (!canonicalCurrent.equals(canonicalExpected))
            {
                return Result.error("precondition failed", path);
            }
            if (!currentPoseBone.equals(expectedPose.get(bone)))
            {
                return Result.error("precondition failed", uuid + "/poseTrack/" + bone);
            }

            needsWrite = true;
        }

        return Result.ok(!desired.entrySet().isEmpty(), needsWrite);
    }

    private static JsonObject requiredObject(JsonObject parent, String key)
    {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject objectOrNull(JsonElement element)
    {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject objectOrEmpty(JsonElement element)
    {
        JsonObject value = objectOrNull(element);
        return value == null ? new JsonObject() : value;
    }

    static final class Result
    {
        final String error;
        final String conflict;
        final boolean seen;
        final boolean needsWrite;

        private Result(String error, String conflict, boolean seen, boolean needsWrite)
        {
            this.error = error;
            this.conflict = conflict;
            this.seen = seen;
            this.needsWrite = needsWrite;
        }

        static Result empty()
        {
            return new Result("", "", false, false);
        }

        static Result ok(boolean seen, boolean needsWrite)
        {
            return new Result("", "", seen, needsWrite);
        }

        static Result error(String error, String conflict)
        {
            return new Result(error, conflict, true, false);
        }
    }
}
