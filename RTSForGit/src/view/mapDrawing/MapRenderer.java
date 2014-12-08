package view.mapDrawing;

import geometry.Point2D;
import geometry.Polygon;
import geometry3D.PolygonExtruder;


import jme3tools.optimize.GeometryBatchFactory;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import collections.PointRing;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.Collidable;
import com.jme3.collision.CollisionResults;
import com.jme3.collision.UnsupportedCollisionException;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.util.TangentBinormalGenerator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;

import math.Angle;
import model.lighting.SunLight;
import model.map.cliff.Trinket;
import model.map.cliff.Cliff;
import model.map.cliff.faces.natural.NaturalFaceMesh;
import model.map.Map;
import model.map.editor.MapToolManager;
import model.map.parcel.ParcelManager;
import model.map.Tile;
import static model.map.cliff.Cliff.Type.Corner;
import static model.map.cliff.Cliff.Type.Orthogonal;
import static model.map.cliff.Cliff.Type.Salient;
import model.map.cliff.faces.manmade.ManmadeFace;
import model.map.cliff.faces.natural.NaturalFace;
import model.map.parcel.ParcelMesh;

import tools.LogUtil;
import view.material.MaterialManager;
import view.math.Translator;

public class MapRenderer implements ActionListener {

    Map map;
    ParcelManager parcelManager;
    MaterialManager mm;
    AssetManager am;
    public Node mainNode = new Node();
    public Node castAndReceiveNode = new Node();
    public Node receiveNode = new Node();
    public PhysicsSpace mainPhysicsSpace = new PhysicsSpace();
    private HashMap<String, Spatial> models = new HashMap<>();

    private HashMap<ParcelMesh, Spatial> parcelsSpatial = new HashMap<>();
    private HashMap<Tile, Spatial> tilesSpatial = new HashMap<>();
	
    public MapRenderer(Map map, ParcelManager parcelManager, MaterialManager mm, AssetManager am) {
        this.map = map;
        this.parcelManager = parcelManager;
        this.mm = mm;
        this.am = am;
        castAndReceiveNode.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        receiveNode.setShadowMode(RenderQueue.ShadowMode.Receive);
        mainNode.attachChild(castAndReceiveNode);
        mainNode.attachChild(receiveNode);
    }
	
    public void renderTiles() {
            LogUtil.logger.info("rendering ground");
            for(ParcelMesh mesh : parcelManager.meshes){
                Geometry g = new Geometry();
                Mesh jmeMesh = Translator.toJMEMesh(mesh);
//                TangentBinormalGenerator.generate(mesh);
                g.setMesh(jmeMesh);
                g.setMaterial(mm.getTerrain("textures/alphamap.png",
                        "textures/grass.png",
                        "textures/road.jpg",
                        "textures/road.jpg",
                        "textures/road_normal.png"));
//                g.addControl(new RigidBodyControl(0));
                parcelsSpatial.put(mesh, g);
                castAndReceiveNode.attachChild(g);
//                mainPhysicsSpace.add(g);
            }
    }

    private Spatial getModel(String path){
        if(!models.containsKey(path))
            models.put(path, am.loadModel(path));
        return models.get(path).clone();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch(e.getActionCommand()){
            case "parcels" : updateParcelsFor((ArrayList<Tile>)(e.getSource())); break;
            case "tiles" : updateTiles((ArrayList<Tile>)(e.getSource())); break;
                default: throw new IllegalArgumentException("Unknown command : "+e.getActionCommand());
        }
    }
    
    private void updateTiles(ArrayList<Tile> tiles){
        for(Tile t : tiles){
            freeTileNode(t);
            if(t.isCliff()){
                if(t.cliff.type == Cliff.Type.Bugged)
                    attachBuggedCliff(t);
                else if(t.cliff.face == null)
                    continue;
                else if(t.cliff.face.getType().equals("natural"))
                    attachNaturalCliff(t);
                else if(t.cliff.face.getType().equals("manmade"))
                    attachManmadeCliff(t);
            }
        }
    }
    
    private void freeTileNode(Tile t){
        Node n = (Node)tilesSpatial.get(t);
        if(n != null)
            castAndReceiveNode.detachChild(n);
        tilesSpatial.remove(t);
    }
    
    private void attachBuggedCliff(Tile t){
        Geometry g = new Geometry();
        g.setMesh(new Box(0.5f, 0.5f, 1));
        g.setMaterial(mm.redMaterial);
        g.setLocalTranslation(t.x+0.5f, t.y+0.5f, (float)(t.level*Tile.STAGE_HEIGHT)+1);
        
        Node n = new Node();
        n.attachChild(g);
        tilesSpatial.put(t, n);
        castAndReceiveNode.attachChild(n);
    }
    
    private void attachNaturalCliff(Tile t){
        Node n = new Node();
        tilesSpatial.put(t, n);
        castAndReceiveNode.attachChild(n);

        NaturalFace face = (NaturalFace)(t.cliff.face);
        Geometry g = new Geometry();
        g.setMesh(Translator.toJMEMesh(face.mesh));
        if(face.color != null)
            g.setMaterial(mm.getLightingColor(Translator.toColorRGBA(face.color)));
        else
            g.setMaterial(mm.getLightingTexture(face.texturePath));
//            g.setMaterial(mm.getLightingTexture("textures/road.jpg"));
        g.rotate(0, 0, (float)(t.cliff.angle));
        g.setLocalTranslation(t.x+0.5f, t.y+0.5f, (float)(t.level*Tile.STAGE_HEIGHT));
        n.attachChild(g);
        attachTrinkets(t);
    }
    
    private void attachManmadeCliff(Tile t){
        Node n = new Node();
        tilesSpatial.put(t, n);
        castAndReceiveNode.attachChild(n);

        ManmadeFace face = (ManmadeFace)(t.cliff.face);
        Spatial s = getModel(face.modelPath);
        if(s == null){
            LogUtil.logger.warning("Can't find model "+face.modelPath);
            return;
        }
        switch (t.cliff.type){
            case Orthogonal : 
                s.rotate(0, 0, (float) (t.cliff.angle+Angle.RIGHT));
                break;
            case Salient : 
                s.rotate(0, 0, (float)(t.cliff.angle+Angle.RIGHT));
                break;
            case Corner : 
                s.rotate(0, 0, (float)(t.cliff.angle));
                break;
        }
        s.scale(0.005f);
        s.setLocalTranslation(t.x+0.5f, t.y+0.5f, (float)(t.level*Tile.STAGE_HEIGHT)+0.1f);
        n.attachChild(s);
    }
    
    private void attachTrinkets(Tile t){
        Node n = (Node)tilesSpatial.get(t);
        if(n == null)
            throw new RuntimeException("Can't attach trinkets before face.");
        
        for(Trinket trinket : t.cliff.trinkets){
            Spatial s = getModel(trinket.modelPath);
            s.scale(0.002f*(float)trinket.scaleX, 0.002f*(float)trinket.scaleY, 0.002f*(float)trinket.scaleZ);
            s.rotate((float)trinket.rotX, (float)trinket.rotY, (float)trinket.rotZ);
            if(trinket.color != null)
                s.setMaterial(mm.getLightingColor(Translator.toColorRGBA(trinket.color)));
            s.setLocalTranslation(Translator.toVector3f(trinket.pos.getAddition(t.x, t.y, t.level*Tile.STAGE_HEIGHT)));
            n.attachChild(s);
        }
        
    }
    
    private void updateParcelsFor(ArrayList<Tile> tiles){
        for(ParcelMesh parcel : parcelManager.getParcelsFor(tiles)){
            Geometry g = (Geometry)parcelsSpatial.get(parcel);
            g.setMesh(Translator.toJMEMesh(parcel));
        }
    }
    
}
