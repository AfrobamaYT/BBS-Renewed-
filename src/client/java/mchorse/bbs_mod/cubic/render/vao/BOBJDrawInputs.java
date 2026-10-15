package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.integrations.vitrail.VitrailMeshInputs;

/** One real pose selection for the preserved draw and the Vitrail GPU snapshot. */
record BOBJDrawInputs(BOBJModelVAO owner, GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer light,
    GpuBuffer indices, int count, BOBJGpuSkin skin, GpuBuffer palette)
{
    void draw(RenderPass pass)
    {
        if (IrisModelBuffers.tryDraw(pass, this.positions, this.normals, this.uvs, this.indices, this.count)) return;
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_POSITION, this.positions.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_NORMALE, this.normals.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_LICHT, this.light.slice());
        pass.setVertexBuffer(ModelVAOPipeline.BINDUNG_UV0, this.uvs.slice());
        if (this.skin != null) this.skin.bind(pass);
        if (this.indices != null)
        {
            pass.setIndexBuffer(this.indices, VertexFormat.IndexType.INT);
            pass.drawIndexed(this.count, 1, 0, 0, 0);
        }
        else pass.draw(this.count, 1, 0, 0);
    }

    VitrailMeshInputs vitrailInputs(GpuBuffer corners, int vertices)
    {
        if (this.palette == null)
            return VitrailMeshInputs.model(corners, this.count, vertices,
                this.positions, this.normals, this.uvs, this.indices);
        var influences = this.skin.vitrailInputs();
        return VitrailMeshInputs.skin(corners, this.count, vertices,
            this.positions, this.normals, this.uvs, this.indices, this.palette,
            influences.joints0(), influences.joints1(), influences.weights0(), influences.weights1());
    }
}
