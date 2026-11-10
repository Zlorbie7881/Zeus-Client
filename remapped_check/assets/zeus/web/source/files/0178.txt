package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.mods.SchematicData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Schematic browser, bounded NBT importer, isometric block preview and material breakdown. */
public final class SchematicLibraryScreen extends Screen {
    private final Screen parent;
    private final Path folder;
    private final Path litematicaFolder;
    private List<Path> files = new ArrayList<>();
    private SchematicData data;
    private String error;
    private int scroll;
    private float zoom = 5.0f;
    private int rotation;

    public SchematicLibraryScreen(Screen parent, Path folder, Path litematicaFolder) {
        super(Component.literal("Zeus Schematic Library")); this.parent=parent; this.folder=folder; this.litematicaFolder=litematicaFolder; scan();
    }
    private void scan() {
        try {
            Files.createDirectories(folder);
            List<Path> all = new ArrayList<>();
            for (Path root : List.of(folder, litematicaFolder)) {
                if (!Files.isDirectory(root)) continue;
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n=p.getFileName().toString().toLowerCase();
                        return n.endsWith(".litematic")||n.endsWith(".schem");
                    }).forEach(all::add);
                }
            }
            files=all.stream().distinct().sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase())).limit(200).toList();
        } catch(IOException e){error="Cannot read library: "+e.getMessage();}
    }
    private void load(Path p) { try { data=SchematicData.read(p); error=null; zoom=Math.max(2f,Math.min(10f,180f/Math.max(1,Math.max(data.width,Math.max(data.height,data.length))))); } catch(Exception e){data=null;error="Import failed: "+e.getMessage();} }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        g.fill(0,0,width,height,0xF0080D18); Draw.bold(g,"ZEUS  /  SCHEMATIC LIBRARY",14,12,0xFFEAF1FF);
        g.drawString(font,"Imports Zeus library and Litematica game-directory schematics folder",14,28,0xFF91A4C2,false);
        int folderLeft = width - 154;
        g.fill(folderLeft, 8, width - 10, 34, 0xFF1D3B5F);
        g.fill(folderLeft, 8, width - 10, 10, 0xFF70C6FF);
        g.drawCenteredString(font, "OPEN SCHEMATICS FOLDER", folderLeft + 72, 18, 0xFFFFFFFF);
        g.fill(10,48,Math.min(width-230,220),height-12,0xE5121C2B);
        g.drawString(font,"LIBRARY · "+files.size(),20,58,0xFF8FB8F0,false);
        int y=80;
        for(int i=scroll;i<Math.min(files.size(),scroll+Math.max(1,(height-115)/19));i++){
            Path p=files.get(i); boolean selected=data!=null&&data.name.equals(p.getFileName().toString());
            if(selected)g.fill(16,y-3,Math.min(width-238,214),y+13,0xFF23436B);
            g.drawString(font,p.getFileName().toString(),22,y,selected?0xFFFFFFFF:0xFFB8C5D8,false); y+=19;
        }
        int left=Math.min(width-230,220)+18;
        if(data==null){
            g.drawString(font,"Select a schematic to import and preview it.",left,60,0xFFDDE7F7,false);
            if(error!=null)g.drawString(font,error,left,82,0xFFFFA66B,false);
            g.drawString(font,"Supported: Sponge v2/v3 and Litematica NBT",left,104,0xFF8EA4C4,false);
            g.drawString(font,"Zeus: config/zeus/schematics",left,124,0xFF8EA4C4,false);
            g.drawString(font,"Litematica: .minecraft/schematics",left,140,0xFF8EA4C4,false);
            g.drawString(font,"Click a file in the library to load it.",left,156,0xFF8EA4C4,false);
        } else {
            g.drawString(font,data.name+"  ·  "+data.width+" × "+data.height+" × "+data.length,left,58,0xFFFFFFFF,false);
            g.drawString(font,data.cells.size()+" non-air blocks  ·  "+data.materials.size()+" materials",left,74,0xFF9DB3D5,false);
            int viewRight=width-190, viewBottom=height-20;
            g.fill(left,94,viewRight,viewBottom,0xCC050912);
            drawPreview(g,left,94,viewRight-left,viewBottom-94);
            int rx=width-178; g.fill(rx,48,width-10,height-12,0xE5121C2B);
            g.drawString(font,"MATERIALS",rx+10,58,0xFF8FB8F0,false);
            int myy=80, count=0;
            for(Map.Entry<String,Integer> e:data.materials.entrySet()){
                if(count++>=Math.max(1,(height-125)/14))break;
                String label=e.getKey().replace("minecraft:","").replace('_',' ');
                if(label.length()>17)label=label.substring(0,17);
                int have=inventoryCount(e.getKey());
                g.drawString(font,label,rx+10,myy,0xFFD8E2F2,false);
                String n=have+" / "+e.getValue();
                g.drawString(font,n,width-20-font.width(n),myy,have>=e.getValue()?0xFF72DDA1:0xFFFFB86B,false); myy+=14;
            }
            if(data.materials.size()>count)g.drawString(font,"+ "+(data.materials.size()-count)+" more",rx+10,myy+3,0xFF91A4C2,false);
            g.drawString(font,"R rotate · +/- zoom",left,viewBottom+5,0xFF8EA4C4,false);
        }
        g.drawString(font,"ESC close  ·  Click a file to load  ·  Refresh folder by reopening",14,height-18,0xFF8EA4C4,false);
        super.render(g,mx,my,partial);
    }

    private int inventoryCount(String state) {
        if(minecraft==null||minecraft.player==null)return 0;
        try {
            int prop=state.indexOf('['); if(prop>=0)state=state.substring(0,prop);
            int colon=state.indexOf(':');
            net.minecraft.resources.Identifier id=colon<0?net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",state):net.minecraft.resources.Identifier.fromNamespaceAndPath(state.substring(0,colon),state.substring(colon+1));
            net.minecraft.world.item.Item item=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id).asItem();
            int total=0; net.minecraft.world.entity.player.Inventory inventory=minecraft.player.getInventory(); for(int slot=0;slot<inventory.getContainerSize();slot++){net.minecraft.world.item.ItemStack stack=inventory.getItem(slot); if(!stack.isEmpty()&&stack.is(item))total+=stack.getCount();}
            return total;
        } catch(RuntimeException e){return 0;}
    }

    private void drawPreview(GuiGraphics g,int x,int y,int w,int h){
        if(data==null)return;
        int cx=x+w/2, base=y+h/2+Math.min(90,data.height*2);
        int scale=Math.max(2,Math.round(zoom));
        List<SchematicData.Cell> cells=new ArrayList<>(data.cells);
        cells.sort(Comparator.comparingInt(c->c.x()+c.y()+c.z()));
        int cap=Math.min(cells.size(),6000);
        for(int i=0;i<cap;i++){
            SchematicData.Cell c=cells.get(i); int ax=c.x(),az=c.z();
            if(rotation==1){int t=ax;ax=data.length-1-az;az=t;} else if(rotation==2){ax=data.width-1-ax;az=data.length-1-az;} else if(rotation==3){int t=ax;ax=az;az=data.width-1-t;}
            int px=cx+(ax-az)*scale, py=base-(ax+az)*scale/2-c.y()*scale;
            if(px<x-8||px>x+w||py<y-8||py>y+h)continue;
            int col=0xFF000000 | (c.state().hashCode()*0x45D9F3B & 0x00FFFFFF); col=0xFF000000|((col&0x00FEFEFE)>>>1)|0x00303030;
            g.fill(px,py,px+scale+1,py+scale+1,col);
            g.fill(px+scale,py+2,px+scale+2,py+scale+3,0xFF111820);
        }
        if(cells.size()>cap)g.drawString(font,"Preview capped at 6,000 blocks for performance",x+8,y+h-14,0xFFFFC66D,false);
    }
    private void openFolder() {
        try {
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder.toFile());
            else error = "This system cannot open folders from the game.";
        } catch (Exception ex) { error = "Could not open folder: " + ex.getMessage(); }
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(e.button()==0 && e.x()>=width-154 && e.x()<width-10 && e.y()>=8 && e.y()<36){openFolder();return true;}
        if(e.button()==0 && e.x()<220 && e.y()>=76){int i=scroll+(int)((e.y()-77)/19);if(i>=0&&i<files.size())load(files.get(i));return true;}
        return super.mouseClicked(e,dbl);
    }
    @Override public boolean mouseScrolled(double x,double y,double scrollX,double scrollY){
        if(scrollY==0)return super.mouseScrolled(x,y,scrollX,scrollY);
        int visible=Math.max(1,(height-115)/19);
        scroll=Math.max(0,Math.min(Math.max(0,files.size()-visible),scroll-(int)Math.signum(scrollY)*3));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(data!=null){int key=e.key(); if(key==82){rotation=(rotation+1)&3;return true;} if(key==61||key==334){zoom=Math.min(16,zoom+1);return true;} if(key==45||key==333){zoom=Math.max(2,zoom-1);return true;}}
        return super.keyPressed(e);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
}
