package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * A CEM part's boxes and sprites with their UVs: box format (mirrored on {@code mirrorTexture "u"}) or
 * per face; a uniform {@code sizeAdd} becomes the cube's inflate, a per-axis {@code sizesAdd} is baked
 * into the geometry. A box without six coordinates is skipped with a warning. From BBS FS b44e56c87,
 * 562c23747 (Wemppy4/bbs-fs, MIT).
 */
final class JemBoxes
{
    private JemBoxes()
    {}

    static ModelCube parseBox(JemParse parse, JsonObject object, Vector3f offset, boolean mirror)
    {
        JsonArray coords = coordinates(parse, object, "box");

        if (coords == null)
        {
            return null;
        }

        Vector3f size = new Vector3f(coords.get(3).getAsFloat(), coords.get(4).getAsFloat(), coords.get(5).getAsFloat());

        ModelCube cube = new ModelCube();

        cube.size.set(size);
        cube.origin.set(coords.get(0).getAsFloat() + offset.x, coords.get(1).getAsFloat() + offset.y, coords.get(2).getAsFloat() + offset.z);
        cube.pivot.set(cube.origin);

        /* The UV is laid out from the box's authored size - growth never changes it. */
        setupBoxUV(cube, object, mirror);

        if (object.has("sizeAdd"))
        {
            cube.inflate = object.get("sizeAdd").getAsFloat();
        }

        if (object.has("sizesAdd"))
        {
            grow(cube, object.getAsJsonArray("sizesAdd"));
        }

        cube.generateQuads(parse.model.textureWidth, parse.model.textureHeight);

        return cube;
    }

    /**
     * Per-axis growth ({@code sizesAdd}) has no native counterpart - the cube's inflate is uniform - so
     * it is baked into the geometry: the box grows by the amount on both sides of every axis.
     */
    static void grow(ModelCube cube, JsonArray sizesAdd)
    {
        float x = sizesAdd.get(0).getAsFloat();
        float y = sizesAdd.get(1).getAsFloat();
        float z = sizesAdd.get(2).getAsFloat();

        cube.origin.sub(x, y, z);
        cube.pivot.set(cube.origin);
        cube.size.add(x * 2, y * 2, z * 2);
    }

    /** CEM sprites are flat textured quads (a box of depth 1) using a single texture offset. */
    static ModelCube parseSprite(JemParse parse, JsonObject object, Vector3f offset)
    {
        JsonArray coords = coordinates(parse, object, "sprite");

        if (coords == null)
        {
            return null;
        }

        Vector3f size = new Vector3f(coords.get(3).getAsFloat(), coords.get(4).getAsFloat(), coords.get(5).getAsFloat());

        ModelCube cube = new ModelCube();

        cube.size.set(size);
        cube.origin.set(coords.get(0).getAsFloat() + offset.x, coords.get(1).getAsFloat() + offset.y, coords.get(2).getAsFloat() + offset.z);
        cube.pivot.set(cube.origin);

        if (object.has("textureOffset"))
        {
            JsonArray uv = object.getAsJsonArray("textureOffset");
            float u = uv.get(0).getAsFloat();
            float v = uv.get(1).getAsFloat();

            cube.front = ModelUV.fromXY(u, v, u + size.x, v + size.y);
            cube.back = ModelUV.fromXY(u + size.x, v, u, v + size.y);
        }

        cube.generateQuads(parse.model.textureWidth, parse.model.textureHeight);

        return cube;
    }

    /** A box's/sprite's six {@code coordinates}, or null (with a warning) when they are missing or short - one bad box must not cost the model. */
    static JsonArray coordinates(JemParse parse, JsonObject object, String what)
    {
        JsonArray coords = object.has("coordinates") && object.get("coordinates").isJsonArray() ? object.getAsJsonArray("coordinates") : null;

        if (coords == null || coords.size() < 6)
        {
            parse.warn("a " + what + " without six coordinates was skipped");

            return null;
        }

        return coords;
    }

    static void setupBoxUV(ModelCube cube, JsonObject object, boolean mirror)
    {
        if (object.has("textureOffset"))
        {
            JsonArray uv = object.getAsJsonArray("textureOffset");

            cube.setupBoxUV(new Vector2f(uv.get(0).getAsFloat(), uv.get(1).getAsFloat()), mirror);

            return;
        }

        cube.front = parseFaceUV(object, "uvNorth", "uvFront");
        cube.back = parseFaceUV(object, "uvSouth", "uvBack");
        cube.left = parseFaceUV(object, "uvWest", "uvLeft");
        cube.right = parseFaceUV(object, "uvEast", "uvRight");
        cube.top = parseFaceUV(object, "uvUp", null);
        cube.bottom = parseFaceUV(object, "uvDown", null);
    }

    static ModelUV parseFaceUV(JsonObject object, String key, String alias)
    {
        JsonArray uv = null;

        if (object.has(key))
        {
            uv = object.getAsJsonArray(key);
        }
        else if (alias != null && object.has(alias))
        {
            uv = object.getAsJsonArray(alias);
        }

        if (uv == null || uv.size() < 4)
        {
            return null;
        }

        return ModelUV.fromXY(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat());
    }
}
