package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionManager;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.film.replays.Inventory;
import mchorse.bbs_mod.actions.ActionRecorder;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.actions.PlayerType;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.entity.IEntityFormProvider;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.EnumUtils;
import mchorse.bbs_mod.utils.PermissionUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import mchorse.bbs_mod.compat.netz.Nutzlast;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.component.CustomData;

public class ServerNetwork
{
    public static final int STATE_TRIGGER_MORPH = 0;
    public static final int STATE_TRIGGER_MAIN_HAND_ITEM = 1;
    public static final int STATE_TRIGGER_OFF_HAND_ITEM = 2;

    public static final ResourceLocation CLIENT_CLICKED_MODEL_BLOCK_PACKET = new ResourceLocation(BBSMod.MOD_ID, "c1");
    public static final ResourceLocation CLIENT_PLAYER_FORM_PACKET = new ResourceLocation(BBSMod.MOD_ID, "c2");
    public static final ResourceLocation CLIENT_PLAY_FILM_PACKET = new ResourceLocation(BBSMod.MOD_ID, "c3");
    public static final ResourceLocation CLIENT_MANAGER_DATA_PACKET = new ResourceLocation(BBSMod.MOD_ID, "c4");
    public static final ResourceLocation CLIENT_STOP_FILM_PACKET = new ResourceLocation(BBSMod.MOD_ID, "c5");
    public static final ResourceLocation CLIENT_HANDSHAKE = new ResourceLocation(BBSMod.MOD_ID, "c6");
    public static final ResourceLocation CLIENT_RECORDED_ACTIONS = new ResourceLocation(BBSMod.MOD_ID, "c7");
    public static final ResourceLocation CLIENT_ANIMATION_STATE_TRIGGER = new ResourceLocation(BBSMod.MOD_ID, "c8");
    public static final ResourceLocation CLIENT_CHEATS_PERMISSION = new ResourceLocation(BBSMod.MOD_ID, "c9");
    public static final ResourceLocation CLIENT_SHARED_FORM = new ResourceLocation(BBSMod.MOD_ID, "c10");
    public static final ResourceLocation CLIENT_ENTITY_FORM = new ResourceLocation(BBSMod.MOD_ID, "c11");
    public static final ResourceLocation CLIENT_ACTORS = new ResourceLocation(BBSMod.MOD_ID, "c12");
    public static final ResourceLocation CLIENT_GUN_PROPERTIES = new ResourceLocation(BBSMod.MOD_ID, "c13");
    public static final ResourceLocation CLIENT_PAUSE_FILM = new ResourceLocation(BBSMod.MOD_ID, "c14");
    public static final ResourceLocation CLIENT_SELECTED_SLOT = new ResourceLocation(BBSMod.MOD_ID, "c15");
    public static final ResourceLocation CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER = new ResourceLocation(BBSMod.MOD_ID, "c16");
    public static final ResourceLocation CLIENT_REFRESH_MODEL_BLOCKS = new ResourceLocation(BBSMod.MOD_ID, "c17");
    public static final ResourceLocation CLIENT_REQUEST_FILM_RESYNC = new ResourceLocation(BBSMod.MOD_ID, "c18");

    public static final ResourceLocation SERVER_MODEL_BLOCK_FORM_PACKET = new ResourceLocation(BBSMod.MOD_ID, "s1");
    public static final ResourceLocation SERVER_MODEL_BLOCK_TRANSFORMS_PACKET = new ResourceLocation(BBSMod.MOD_ID, "s2");
    public static final ResourceLocation SERVER_PLAYER_FORM_PACKET = new ResourceLocation(BBSMod.MOD_ID, "s3");
    public static final ResourceLocation SERVER_MANAGER_DATA_PACKET = new ResourceLocation(BBSMod.MOD_ID, "s4");
    public static final ResourceLocation SERVER_ACTION_RECORDING = new ResourceLocation(BBSMod.MOD_ID, "s5");
    public static final ResourceLocation SERVER_TOGGLE_FILM = new ResourceLocation(BBSMod.MOD_ID, "s6");
    public static final ResourceLocation SERVER_ACTION_CONTROL = new ResourceLocation(BBSMod.MOD_ID, "s7");
    public static final ResourceLocation SERVER_FILM_DATA_SYNC = new ResourceLocation(BBSMod.MOD_ID, "s8");
    public static final ResourceLocation SERVER_PLAYER_TP = new ResourceLocation(BBSMod.MOD_ID, "s9");
    public static final ResourceLocation SERVER_ANIMATION_STATE_TRIGGER = new ResourceLocation(BBSMod.MOD_ID, "s10");
    public static final ResourceLocation SERVER_SHARED_FORM = new ResourceLocation(BBSMod.MOD_ID, "s11");
    public static final ResourceLocation SERVER_ZOOM = new ResourceLocation(BBSMod.MOD_ID, "s12");
    public static final ResourceLocation SERVER_PAUSE_FILM = new ResourceLocation(BBSMod.MOD_ID, "s13");
    public static final ResourceLocation SERVER_APPLY_FILM_PLAYER_SETTINGS = new ResourceLocation(BBSMod.MOD_ID, "s14");

    private static ServerPacketCrusher crusher = new ServerPacketCrusher();

    public static void reset()
    {
        crusher.reset();
    }

    public static Nutzlast.Type<BufPayload> idFor(ResourceLocation identifier)
    {
        return new Nutzlast.Type<>(identifier);
    }

    private static void registerC2S(ResourceLocation identifier)
    {
        PayloadTypeRegistry.playC2S().register(idFor(identifier), BufPayload.codecFor(idFor(identifier)));
    }

    private static void registerS2C(ResourceLocation identifier)
    {
        PayloadTypeRegistry.playS2C().register(idFor(identifier), BufPayload.codecFor(idFor(identifier)));
    }

    public static void setup()
    {
        registerC2S(SERVER_MODEL_BLOCK_FORM_PACKET);
        registerC2S(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET);
        registerC2S(SERVER_PLAYER_FORM_PACKET);
        registerC2S(SERVER_MANAGER_DATA_PACKET);
        registerC2S(SERVER_ACTION_RECORDING);
        registerC2S(SERVER_TOGGLE_FILM);
        registerC2S(SERVER_ACTION_CONTROL);
        registerC2S(SERVER_FILM_DATA_SYNC);
        registerC2S(SERVER_PLAYER_TP);
        registerC2S(SERVER_ANIMATION_STATE_TRIGGER);
        registerC2S(SERVER_SHARED_FORM);
        registerC2S(SERVER_ZOOM);
        registerC2S(SERVER_PAUSE_FILM);
        registerC2S(SERVER_APPLY_FILM_PLAYER_SETTINGS);

        registerS2C(CLIENT_CLICKED_MODEL_BLOCK_PACKET);
        registerS2C(CLIENT_PLAYER_FORM_PACKET);
        registerS2C(CLIENT_PLAY_FILM_PACKET);
        registerS2C(CLIENT_MANAGER_DATA_PACKET);
        registerS2C(CLIENT_STOP_FILM_PACKET);
        registerS2C(CLIENT_HANDSHAKE);
        registerS2C(CLIENT_RECORDED_ACTIONS);
        registerS2C(CLIENT_ANIMATION_STATE_TRIGGER);
        registerS2C(CLIENT_CHEATS_PERMISSION);
        registerS2C(CLIENT_SHARED_FORM);
        registerS2C(CLIENT_ENTITY_FORM);
        registerS2C(CLIENT_ACTORS);
        registerS2C(CLIENT_GUN_PROPERTIES);
        registerS2C(CLIENT_PAUSE_FILM);
        registerS2C(CLIENT_SELECTED_SLOT);
        registerS2C(CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER);
        registerS2C(CLIENT_REFRESH_MODEL_BLOCKS);
        registerS2C(CLIENT_REQUEST_FILM_RESYNC);

        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_MODEL_BLOCK_FORM_PACKET), (payload, context) -> handleModelBlockFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_MODEL_BLOCK_TRANSFORMS_PACKET), (payload, context) -> handleModelBlockTransformsPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_PLAYER_FORM_PACKET), (payload, context) -> handlePlayerFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_MANAGER_DATA_PACKET), (payload, context) -> handleManagerDataPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_ACTION_RECORDING), (payload, context) -> handleActionRecording(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_TOGGLE_FILM), (payload, context) -> handleToggleFilm(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_ACTION_CONTROL), (payload, context) -> handleActionControl(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_FILM_DATA_SYNC), (payload, context) -> handleSyncData(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_PLAYER_TP), (payload, context) -> handleTeleportPlayer(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_ANIMATION_STATE_TRIGGER), (payload, context) -> handleAnimationStateTriggerPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_SHARED_FORM), (payload, context) -> handleSharedFormPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_ZOOM), (payload, context) -> handleZoomPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_PAUSE_FILM), (payload, context) -> handlePauseFilmPacket(context.server(), context.player(), payload.asPacketByteBuf()));
        mchorse.bbs_mod.compat.netz.NetzServer.empfangen(idFor(SERVER_APPLY_FILM_PLAYER_SETTINGS), (payload, context) -> handleApplyFilmPlayerSettings(context.server(), context.player(), payload.asPacketByteBuf()));
    }

    public record BufPayload(byte[] data, Nutzlast.Type<BufPayload> id) implements Nutzlast
    {
        public static BufPayload from(FriendlyByteBuf buf, Nutzlast.Type<BufPayload> id)
        {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new BufPayload(bytes, id);
        }

        public FriendlyByteBuf asPacketByteBuf()
        {
            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            out.writeBytes(this.data);
            return out;
        }

        @Override
        public Nutzlast.Type<? extends Nutzlast> type()
        {
            return this.id;
        }

        public static StreamCodec<RegistryFriendlyByteBuf, BufPayload> codecFor(Nutzlast.Type<BufPayload> id)
        {
            return new StreamCodec<>()
            {
                @Override
                public BufPayload decode(RegistryFriendlyByteBuf byteBuf)
                {
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    return new BufPayload(bytes, id);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf byteBuf, BufPayload payload)
                {
                    byteBuf.writeBytes(payload.data);
                }
            };
        }
    }

    /* Handlers */

    private static void handleModelBlockFormPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            BlockPos pos = buf.readBlockPos();

            try
            {
                MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    Level world = player.level();
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof ModelBlockEntity modelBlock)
                    {
                        modelBlock.updateForm(data, world);
                    }
                });
            }
            catch (Exception e)
            {}
        });
    }

    private static void handleModelBlockTransformsPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            try
            {
                MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    ItemStack stack = player.getItemBySlot(EquipmentSlot.MAINHAND).copy();

                    if (stack.getItem() == BBSMod.MODEL_BLOCK_ITEM)
                    {
                        net.minecraft.world.item.component.CustomData beComponent = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                        CompoundTag beNbt = beComponent != null ? beComponent.copyTag() : new CompoundTag();

                        beNbt.put("Properties", DataStorageUtils.toNbt(data));
                        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beNbt));
                    }
                    else if (stack.getItem() == BBSMod.GUN_ITEM)
                    {
                        CustomData customComponent = stack.get(DataComponents.CUSTOM_DATA);
                        CompoundTag customNbt = customComponent != null ? customComponent.copyTag() : new CompoundTag();

                        customNbt.put("GunData", DataStorageUtils.toNbt(data));
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customNbt));
                    }

                    player.setItemSlot(EquipmentSlot.MAINHAND, stack);
                });
            }
            catch (Exception e)
            {}
        });
    }

    private static void handlePlayerFormPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            Form form = null;

            try
            {
                if (DataStorageUtils.readFromBytes(bytes) instanceof MapType data)
                {
                    form = BBSMod.getForms().fromData(data);
                }
            }
            catch (Exception e)
            {}

            final Form finalForm = form;

            server.execute(() ->
            {
                Morph.getMorph(player).setForm(FormUtils.copy(finalForm));

                sendMorphToTracked(player, finalForm);
            });
        });
    }

    private static void handleManagerDataPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);
            int callbackId = packetByteBuf.readInt();
            RepositoryOperation op = RepositoryOperation.values()[packetByteBuf.readInt()];
            FilmManager films = BBSMod.getFilms();

            if (op == RepositoryOperation.LOAD)
            {
                String id = data.getString("id");
                Film film = films.load(id);

                sendManagerData(player, callbackId, op, film.toData());
            }
            else if (op == RepositoryOperation.SAVE)
            {
                films.save(data.getString("id"), data.getMap("data"));
            }
            else if (op == RepositoryOperation.RENAME)
            {
                films.rename(data.getString("from"), data.getString("to"));
            }
            else if (op == RepositoryOperation.DELETE)
            {
                films.delete(data.getString("id"));
            }
            else if (op == RepositoryOperation.KEYS)
            {
                ListType list = DataStorageUtils.stringListToData(films.getKeys());

                sendManagerData(player, callbackId, op, list);
            }
            else if (op == RepositoryOperation.ADD_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.addFolder(data.getString("folder"))));
            }
            else if (op == RepositoryOperation.RENAME_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.renameFolder(data.getString("from"), data.getString("to"))));
            }
            else if (op == RepositoryOperation.DELETE_FOLDER)
            {
                sendManagerData(player, callbackId, op, new ByteType(films.deleteFolder(data.getString("folder"))));
            }
        });
    }

    private static void handleActionRecording(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String filmId = buf.readUtf();
        int replayId = buf.readInt();
        int tick = buf.readInt();
        int countdown = buf.readInt();
        boolean recording = buf.readBoolean();

        server.execute(() ->
        {
            if (recording)
            {
                Film film = BBSMod.getFilms().load(filmId);

                if (film != null)
                {
                    BBSMod.getActions().startRecording(film, player, 0, countdown, replayId);
                }
            }
            else
            {
                ActionRecorder recorder = BBSMod.getActions().stopRecording(player);
                Clips clips = recorder.composeClips();

                /* Send recorded clips to the client */
                sendRecordedActions(player, filmId, replayId, tick, clips);
            }
        });
    }

    private static void handleToggleFilm(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        String filmId = buf.readUtf();
        boolean withCamera = buf.readBoolean();

        server.execute(() ->
        {
            ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(filmId);

            if (actionPlayer != null)
            {
                BBSMod.getActions().stop(filmId);

                for (ServerPlayer otherPlayer : server.getPlayerList().getPlayers())
                {
                    sendStopFilm(otherPlayer, filmId);
                }
            }
            else
            {
                sendPlayFilm(player, player.serverLevel(), filmId, withCamera);
            }
        });
    }

    private static void handleActionControl(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        ActionManager actions = BBSMod.getActions();
        String filmId = buf.readUtf();
        ActionState state = EnumUtils.getValue(buf.readByte(), ActionState.values(), ActionState.STOP);
        int tick = buf.readInt();

        server.execute(() ->
        {
            if (state == ActionState.SEEK)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                }
            }
            else if (state == ActionState.PLAY)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                    actionPlayer.playing = true;
                }
            }
            else if (state == ActionState.PAUSE)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer != null)
                {
                    actionPlayer.goTo(tick);
                    actionPlayer.playing = false;
                }
            }
            else if (state == ActionState.RESTART)
            {
                ActionPlayer actionPlayer = actions.getPlayer(filmId);

                if (actionPlayer == null)
                {
                    Film film = BBSMod.getFilms().load(filmId);

                    if (film != null)
                    {
                        actionPlayer = actions.play(player, player.serverLevel(), film, tick, PlayerType.FILM_EDITOR);
                    }
                }
                else
                {
                    actions.stop(filmId);

                    actionPlayer = actions.play(player, player.serverLevel(), actionPlayer.film, tick, PlayerType.FILM_EDITOR);
                }

                if (actionPlayer != null)
                {
                    actionPlayer.syncing = true;
                    actionPlayer.playing = false;

                    if (tick != 0)
                    {
                        actionPlayer.goTo(0, tick);
                    }
                }

                sendStopFilm(player, filmId);
            }
            else if (state == ActionState.STOP)
            {
                actions.stop(filmId);
            }
        });
    }

    private static void handleSyncData(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            String filmId = packetByteBuf.readUtf();
            List<String> path = new ArrayList<>();

            for (int i = 0, c = buf.readInt(); i < c; i++)
            {
                path.add(buf.readUtf());
            }

            BaseType data = DataStorageUtils.readFromBytes(bytes);

            server.execute(() ->
            {
                BBSMod.getActions().syncData(filmId, new DataPath(path), data);
            });
        });
    }

    private static void handleTeleportPlayer(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float yaw = buf.readFloat();
        float bodyYaw = buf.readFloat();
        float pitch = buf.readFloat();

        server.execute(() ->
        {
            player.teleportTo(x, y, z);

            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setYBodyRot(bodyYaw);
            player.setXRot(pitch);
        });
    }

    private static void handleAnimationStateTriggerPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        String string = buf.readUtf();
        int type = buf.readInt();
        FriendlyByteBuf newBuf = new FriendlyByteBuf(Unpooled.buffer());

        newBuf.writeInt(player.getId());
        newBuf.writeUtf(string);
        newBuf.writeInt(type);

        for (ServerPlayer otherPlayer : PlayerLookup.tracking(player))
        {
            mchorse.bbs_mod.compat.netz.NetzServer.senden(otherPlayer, BufPayload.from(newBuf, idFor(CLIENT_ANIMATION_STATE_TRIGGER)));
        }

        server.execute(() ->
        {
            /* TODO: State Triggers */
        });
    }

    private static void handleSharedFormPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        crusher.receive(buf, (bytes, packetByteBuf) ->
        {
            UUID playerUuid = packetByteBuf.readUUID();
            MapType data = (MapType) DataStorageUtils.readFromBytes(bytes);

            server.execute(() ->
            {
                ServerPlayer otherPlayer = server.getPlayerList().getPlayer(playerUuid);

                if (otherPlayer != null)
                {
                    sendSharedForm(otherPlayer, data);
                }
            });
        });
    }

    private static void handleZoomPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        boolean zoom = buf.readBoolean();
        ItemStack main = player.getMainHandItem();

        if (main.getItem() == BBSMod.GUN_ITEM)
        {
            GunProperties properties = GunProperties.get(main);
            String command = zoom ? properties.cmdZoomOn : properties.cmdZoomOff;

            if (!command.isEmpty())
            {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            }
        }
    }

    private static void handlePauseFilmPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        String filmId = buf.readUtf();

        ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(filmId);

        if (actionPlayer != null)
        {
            actionPlayer.toggle();
        }

        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers())
        {
            sendPauseFilm(playerEntity, filmId);
        }
    }

    private static void handleApplyFilmPlayerSettings(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player))
        {
            return;
        }

        float hp = buf.readFloat();
        float hunger = buf.readFloat();
        int xpLevel = buf.readInt();
        float xpProgress = buf.readFloat();
        int invSize = buf.readInt();
        byte[] invBytes = invSize > 0 ? new byte[invSize] : null;

        if (invBytes != null)
        {
            buf.readBytes(invBytes);
        }

        final byte[] invBytesFinal = invBytes;

        server.execute(() ->
        {
            ActionPlayer.applyFilmPlayerSettingsTo(player, hp, hunger, xpLevel, xpProgress);

            if (invBytesFinal != null)
            {
                BaseType invData = DataStorageUtils.readFromBytes(invBytesFinal);

                if (invData.isList())
                {
                    Inventory.applyToPlayer(player, invData.asList());
                }
            }
        });
    }

    /* API */

    public static void sendMorph(ServerPlayer player, int playerId, Form form)
    {
        crusher.send(player, CLIENT_PLAYER_FORM_PACKET, FormUtils.toData(form), (packetByteBuf) ->
        {
            packetByteBuf.writeInt(playerId);
        });
    }

    public static void sendMorphToTracked(ServerPlayer player, Form form)
    {
        sendMorph(player, player.getId(), form);

        for (ServerPlayer otherPlayer : PlayerLookup.tracking(player))
        {
            sendMorph(otherPlayer, player.getId(), form);
        }
    }

    public static void sendClickedModelBlock(ServerPlayer player, BlockPos pos)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeBlockPos(pos);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_CLICKED_MODEL_BLOCK_PACKET)));
    }

    public static void sendPlayFilm(ServerPlayer player, ServerLevel world, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(player, world, film, 0);

                BaseType data = film.toData();

                crusher.send(world.players().stream().map((p) -> (Player) p).toList(), CLIENT_PLAY_FILM_PACKET, data, (packetByteBuf) ->
                {
                    packetByteBuf.writeUtf(filmId);
                    packetByteBuf.writeBoolean(withCamera);
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void sendPlayFilm(ServerPlayer player, String filmId, boolean withCamera)
    {
        try
        {
            Film film = BBSMod.getFilms().load(filmId);

            if (film != null)
            {
                BBSMod.getActions().play(player, player.serverLevel(), film, 0);

                crusher.send(player, CLIENT_PLAY_FILM_PACKET, film.toData(), (packetByteBuf) ->
                {
                    packetByteBuf.writeUtf(filmId);
                    packetByteBuf.writeBoolean(withCamera);
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void sendStopFilm(ServerPlayer player, String filmId)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeUtf(filmId);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_STOP_FILM_PACKET)));
    }

    /**
     * Ask the editing client to re-send the whole film, used when a per-property
     * sync targets a path the server doesn't have (client/server desync).
     */
    public static void requestFilmResync(ServerPlayer player, String filmId)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeUtf(filmId);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_REQUEST_FILM_RESYNC)));
    }

    public static void sendManagerData(ServerPlayer player, int callbackId, RepositoryOperation op, BaseType data)
    {
        crusher.send(player, CLIENT_MANAGER_DATA_PACKET, data, (packetByteBuf) ->
        {
            packetByteBuf.writeInt(callbackId);
            packetByteBuf.writeInt(op.ordinal());
        });
    }

    public static void sendRecordedActions(ServerPlayer player, String filmId, int replayId, int tick, Clips clips)
    {
        crusher.send(player, CLIENT_RECORDED_ACTIONS, clips.toData(), (packetByteBuf) ->
        {
            packetByteBuf.writeUtf(filmId);
            packetByteBuf.writeInt(replayId);
            packetByteBuf.writeInt(tick);
        });
    }

    public static void sendHandshake(MinecraftServer server, PacketSender packetSender)
    {
        mchorse.bbs_mod.compat.netz.NetzServer.senden(packetSender, BufPayload.from(createHandshakeBuf(server), idFor(ServerNetwork.CLIENT_HANDSHAKE)));
    }

    public static void sendHandshake(MinecraftServer server, ServerPlayer player)
    {
        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(createHandshakeBuf(server), idFor(CLIENT_HANDSHAKE)));
    }

    private static FriendlyByteBuf createHandshakeBuf(MinecraftServer server)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        String id = "";

        /* No need to do that in singleplayer */
        if (server.isSingleplayer())
        {
            id = "";
        }

        buf.writeUtf(id);

        return buf;
    }

    public static void sendCheatsPermission(ServerPlayer player, boolean cheats)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeBoolean(cheats);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_CHEATS_PERMISSION)));
    }

    public static void sendSharedForm(ServerPlayer player, MapType data)
    {
        crusher.send(player, CLIENT_SHARED_FORM, data, (packetByteBuf) ->
        {});
    }

    public static void sendEntityForm(ServerPlayer player, IEntityFormProvider actor)
    {
        crusher.send(player, CLIENT_ENTITY_FORM, FormUtils.toData(actor.getForm()), (packetByteBuf) ->
        {
            packetByteBuf.writeInt(actor.getEntityId());
        });
    }

    public static void sendActors(ServerPlayer player, String filmId, Map<String, LivingEntity> actors)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeUtf(filmId);
        buf.writeInt(actors.size());

        for (Map.Entry<String, LivingEntity> entry : actors.entrySet())
        {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue().getId());
        }

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_ACTORS)));
    }

    public static void sendGunProperties(ServerPlayer player, GunProjectileEntity projectile)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        GunProperties properties = projectile.getProperties();

        buf.writeInt(projectile.getEntityId());
        properties.toNetwork(buf);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_GUN_PROPERTIES)));
    }

    public static void sendPauseFilm(ServerPlayer player, String filmId)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeUtf(filmId);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_PAUSE_FILM)));
    }

    public static void sendSelectedSlot(ServerPlayer player, int slot)
    {
        player.getInventory().selected = slot;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeInt(slot);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_SELECTED_SLOT)));
    }

    public static void sendModelBlockState(ServerPlayer player, BlockPos pos, String trigger)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeBlockPos(pos);
        buf.writeUtf(trigger);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_ANIMATION_STATE_MODEL_BLOCK_TRIGGER)));
    }

    public static void sendReloadModelBlocks(ServerPlayer player, int tickRandom)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeInt(tickRandom);

        mchorse.bbs_mod.compat.netz.NetzServer.senden(player, BufPayload.from(buf, idFor(CLIENT_REFRESH_MODEL_BLOCKS)));
    }
}