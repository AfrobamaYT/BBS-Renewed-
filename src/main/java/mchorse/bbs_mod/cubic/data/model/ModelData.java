package mchorse.bbs_mod.cubic.data.model;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModelData
{
    public List<Vector3f> vertices = new ArrayList<>();
    public List<Vector3f> normals = new ArrayList<>();
    public List<Vector2f> uvs = new ArrayList<>();

    public void clear()
    {
        this.vertices.clear();
        this.normals.clear();
        this.uvs.clear();
    }
}