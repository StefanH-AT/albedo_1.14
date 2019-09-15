package com.hrznstudio.albedo;

import com.hrznstudio.albedo.event.*;
import com.hrznstudio.albedo.lighting.Light;
import com.hrznstudio.albedo.lighting.LightManager;
import com.hrznstudio.albedo.util.ShaderManager;
import com.hrznstudio.albedo.util.ShaderUtil;
import com.hrznstudio.albedo.util.TriConsumer;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.EndGatewayTileEntity;
import net.minecraft.tileentity.EndPortalTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class EventManager {
    private static final Map<BlockPos, List<Light>> EXISTING = Collections.synchronizedMap(new HashMap<>());
    public static boolean isGui = false;
    private int ticks = 0;
    private boolean postedLights = false;
    private boolean precedesEntities = true;
    private String section = "";
    private Thread thread;

    public void startThread() {
        thread = new Thread(() -> {
            while (!thread.isInterrupted()) {
                if (Minecraft.getInstance().player != null) {
                    PlayerEntity player = Minecraft.getInstance().player;
                    if (Minecraft.getInstance().world != null) {
                        IWorldReader reader = Minecraft.getInstance().world;
                        BlockPos playerPos = player.getPosition();
                        int maxDistance = ConfigManager.maxDistance.get();
                        int r = maxDistance / 2;
                        Iterable<BlockPos> posIterable = BlockPos.MutableBlockPos.getAllInBoxMutable(playerPos.add(-r, -r, -r), playerPos.add(r, r, r));
                        for (BlockPos pos : posIterable) {
                            Vec3d cameraPosition = LightManager.cameraPos;
                            ICamera camera = LightManager.camera;
                            BlockState state = reader.getBlockState(pos);
                            ArrayList<Light> lights = new ArrayList<>();
                            GatherLightsEvent lightsEvent = new GatherLightsEvent(lights, maxDistance, cameraPosition, camera);
                            TriConsumer<BlockPos, BlockState, GatherLightsEvent> consumer = Albedo.getLightHandler(state.getBlock());
                            if (consumer != null)
                                consumer.apply(pos, state, lightsEvent);
                            if (lights.isEmpty()) {
                                EXISTING.remove(pos);
                            } else {
                                EXISTING.put(pos.toImmutable(), lights);
                            }
                        }
                    }
                }
            }
        });
        thread.start();
    }

    @SubscribeEvent
    public void onProfilerChange(ProfilerStartEvent event) {
        section = event.getSection();
        if (ConfigManager.isLightingEnabled()) {
            if (event.getSection().compareTo("terrain") == 0) {
                isGui = false;
                precedesEntities = true;
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("ticks", ticks + Minecraft.getInstance().getRenderPartialTicks());
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getInstance().player.posX, (float) Minecraft.getInstance().player.posY, (float) Minecraft.getInstance().player.posZ);
                if (!postedLights) {
                    if (thread == null || !thread.isAlive()) {
                        startThread();
                    }
                    EXISTING.forEach((pos, lights) -> LightManager.lights.addAll(lights));
                    LightManager.update(Minecraft.getInstance().world);
                    ShaderManager.stopShader();
                    MinecraftForge.EVENT_BUS.post(new LightUniformEvent());
                    ShaderUtil.fastLightProgram.useShader();
                    LightManager.uploadLights();
                    ShaderUtil.entityLightProgram.useShader();
                    ShaderUtil.entityLightProgram.setUniform("ticks", ticks + Minecraft.getInstance().getRenderPartialTicks());
                    ShaderUtil.entityLightProgram.setUniform("sampler", 0);
                    ShaderUtil.entityLightProgram.setUniform("lightmap", 1);
                    LightManager.uploadLights();
                    ShaderUtil.entityLightProgram.setUniform("playerPos", (float) Minecraft.getInstance().player.posX, (float) Minecraft.getInstance().player.posY, (float) Minecraft.getInstance().player.posZ);
                    ShaderUtil.entityLightProgram.setUniform("lightingEnabled", GL11.glIsEnabled(GL11.GL_LIGHTING));
                    ShaderUtil.fastLightProgram.useShader();
                    postedLights = true;
                    LightManager.clear();
                }
            }
            if (event.getSection().compareTo("sky") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("litParticles") == 0) {
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getInstance().player.posX, (float) Minecraft.getInstance().player.posY, (float) Minecraft.getInstance().player.posZ);
                ShaderUtil.fastLightProgram.setUniform("chunkX", 0);
                ShaderUtil.fastLightProgram.setUniform("chunkY", 0);
                ShaderUtil.fastLightProgram.setUniform("chunkZ", 0);
            }
            if (event.getSection().compareTo("particles") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("weather") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("entities") == 0) {
                if (Minecraft.getInstance().isOnExecutionThread()) {
                    ShaderUtil.entityLightProgram.useShader();
                    ShaderUtil.entityLightProgram.setUniform("lightingEnabled", true);
                    ShaderUtil.entityLightProgram.setUniform("fogIntensity", Minecraft.getInstance().world.getDimension().getType() == DimensionType.THE_NETHER ? 0.015625f : 1.0f);
                }
            }
            if (event.getSection().compareTo("blockEntities") == 0) {
                if (Minecraft.getInstance().isOnExecutionThread()) {
                    ShaderUtil.entityLightProgram.useShader();
                    ShaderUtil.entityLightProgram.setUniform("lightingEnabled", true);
                }
            }
            if (event.getSection().compareTo("outline") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("aboveClouds") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("destroyProgress") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("translucent") == 0) {
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getInstance().player.posX, (float) Minecraft.getInstance().player.posY, (float) Minecraft.getInstance().player.posZ);
            }
            if (event.getSection().compareTo("hand") == 0) {
                ShaderUtil.entityLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("entityPos", (float) Minecraft.getInstance().player.posX, (float) Minecraft.getInstance().player.posY, (float) Minecraft.getInstance().player.posZ);
                precedesEntities = true;
            }
            if (event.getSection().compareTo("gui") == 0) {
                isGui = true;
                ShaderManager.stopShader();
            }
        }
    }

    @SubscribeEvent
    public void onRenderEntity(RenderEntityEvent event) {
        if (ConfigManager.isLightingEnabled()) {
            if (event.getEntity() instanceof LightningBoltEntity) {
                ShaderManager.stopShader();
            } else if (section.equalsIgnoreCase("entities") || section.equalsIgnoreCase("blockEntities")) {
                ShaderUtil.entityLightProgram.useShader();
            }
            if (ShaderManager.isCurrentShader(ShaderUtil.entityLightProgram)) {
                ShaderUtil.entityLightProgram.setUniform("entityPos", (float) event.getEntity().posX, (float) event.getEntity().posY + event.getEntity().getHeight() / 2.0f, (float) event.getEntity().posZ);
                //ShaderUtil.entityLightProgram.setUniform("colorMult", 1f, 1f, 1f, 0f);
                //if (event.getEntity() instanceof EntityLivingBase) {
                //    EntityLivingBase e = (EntityLivingBase) event.getEntity();
                //    if (e.hurtTime > 0 || e.deathTime > 0) {
                //        ShaderUtil.entityLightProgram.setUniform("colorMult", 1f, 0f, 0f, 0.3f);
                //    }
                //}
            }
        }
    }

    @SubscribeEvent
    public void onRenderTileEntity(RenderTileEntityEvent event) {
        if (ConfigManager.isLightingEnabled()) {
            if (event.getEntity() instanceof EndPortalTileEntity || event.getEntity() instanceof EndGatewayTileEntity) {
                ShaderManager.stopShader();
            } else if (section.equalsIgnoreCase("entities") || section.equalsIgnoreCase("blockEntities")) {
                ShaderUtil.entityLightProgram.useShader();
            }
            if (ShaderManager.isCurrentShader(ShaderUtil.entityLightProgram)) {
                ShaderUtil.entityLightProgram.setUniform("entityPos", (float) event.getEntity().getPos().getX(), (float) event.getEntity().getPos().getY(), (float) event.getEntity().getPos().getZ());
                //ShaderUtil.entityLightProgram.setUniform("colorMult", 1f, 1f, 1f, 0f);
            }
        }
    }

    @SubscribeEvent
    public void onRenderChunk(RenderChunkUniformsEvent event) {
        if (ConfigManager.isLightingEnabled()) {
            if (ShaderManager.isCurrentShader(ShaderUtil.fastLightProgram)) {
                BlockPos pos = event.getChunk().getPosition();
                ShaderUtil.fastLightProgram.setUniform("chunkX", pos.getX());
                ShaderUtil.fastLightProgram.setUniform("chunkY", pos.getY());
                ShaderUtil.fastLightProgram.setUniform("chunkZ", pos.getZ());
            }
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ticks++;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        postedLights = false;
        if (Minecraft.getInstance().isOnExecutionThread()) {
            GlStateManager.disableLighting();
            ShaderManager.stopShader();
        }
    }
}