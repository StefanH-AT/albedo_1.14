package com.hrznstudio.albedo.event;

import net.minecraft.client.renderer.chunk.ChunkRender;
import net.minecraftforge.eventbus.api.Event;

public class RenderChunkUniformsEvent extends Event {
    private final ChunkRender renderChunk;

    public RenderChunkUniformsEvent(ChunkRender r) {
        super();
        this.renderChunk = r;
    }

    public ChunkRender getChunk() {
        return renderChunk;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
