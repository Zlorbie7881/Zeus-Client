package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.mods.GoliathGrid;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

public final class GoliathMapScreen extends Screen {
    private final Screen parent; private final IntConsumer selected;
    private int chosen=-1; private int hover=-1; private int gx,gy,cell;
    public GoliathMapScreen(Screen parent,IntConsumer selected){super(Component.literal("Goliath RTP map"));this.parent=parent;this.selected=selected;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xF0080D18); Draw.bold(g,"ZEUS  /  GOLIATH REGION MAP",14,12,0xFFEAF1FF);
        cell=Math.max(5,Math.min(13,(Math.min(width-250,height-90))/36)); gx=(width-36*cell)/2-70; gy=(height-36*cell)/2+8;
        hover=-1;
        for(int z=0;z<36;z++)for(int x=0;x<36;x++){
            int v=GoliathGrid.square(x,z),r=v<0?-1:v/1000; int color=switch(r){case 0->0xFF3E7FA5;case 1->0xFF397C65;case 2->0xFF8A713C;case 3->0xFF72529A;case 4->0xFF9A554E;case 5->0xFF9A8A35;case 6->0xFF3F8790;default->0xFF202A39;};
            int px=gx+x*cell,py=gy+z*cell;
            if(mx>=px&&mx<px+cell&&my>=py&&my<py+cell)hover=v;
            if(v==chosen)color=0xFFFFD166;
            g.fill(px,py,px+cell-1,py+cell-1,color);
        }
        int lx=gx+36*cell+16; g.drawString(font,"REGIONS",lx,gy,0xFF8FB8F0,false);
        for(int i=0;i<GoliathGrid.REGIONS.length;i++)g.drawString(font,(i+1)+". "+GoliathGrid.REGIONS[i],lx,gy+18+i*16,0xFFD8E2F2,false);
        if(hover>=0){int sq=hover%1000,reg=hover/1000;g.drawString(font,GoliathGrid.REGIONS[reg]+" · Goliath "+sq,lx,gy+145,0xFFFFD166,false);}
        if(chosen>=0)g.drawString(font,"Selected: "+GoliathGrid.REGIONS[chosen],lx,gy+164,0xFF72DDA1,false);
        g.drawString(font,"Click a cell to choose its RTP region. The server selects the final landing square.",14,height-28,0xFF9FB1CB,false);
        g.drawString(font,"ESC: cancel   ENTER: use selected region",14,height-14,0xFF9FB1CB,false);
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){if(e.button()==0){int x=(int)((e.x()-gx)/cell),z=(int)((e.y()-gy)/cell);if(x>=0&&x<36&&z>=0&&z<36){int v=GoliathGrid.square(x,z);if(v>=0)chosen=v/1000;return true;}}return super.mouseClicked(e,dbl);}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e){if(e.key()==257&&chosen>=0){selected.accept(chosen);minecraft.setScreen(parent);return true;}return super.keyPressed(e);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
