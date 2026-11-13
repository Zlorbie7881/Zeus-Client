package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.mods.Hud;
import com.zlorbie788.zeus.ui.Accent;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;

public final class AdvancedFeatures {
    private AdvancedFeatures() {}

    public static final class GoliathRtp extends Module {
        private final Setting.Text command = add(new Setting.Text("RTP command", "The server command used for random teleport. Do not include the leading slash.", "rtp", 48));
        private final Setting.Choice region = add(new Setting.Choice("Goliath region", "These are the Donut Goliath RTP region command names. Europe uses plain /rtp.", 2, "EU Central", "EU West", "Europe", "NA West", "NA East", "Asia", "Oceania"));
        private final Setting.Action openMap = add(new Setting.Action("Open Goliath map", "Browse the 36×36 Goliath grid built into Zeus. Selecting a cell chooses its region; the server still chooses the landing square.", this::showMap));
        private final Setting.Action send = add(new Setting.Action("Request RTP", "Sends one normal server RTP command. The server chooses the destination.", this::request));
        private final Setting.Info location = add(new Setting.Info("Current Goliath", this::locationLines));
        private String status = "Ready";
        private int currentSquare = -1;
        private long lastRequest;

        public GoliathRtp() {
            super("goliathrtp", "Goliath RTP", "Donut/Goliath RTP region command helper using the Goliath region command names. The server still chooses the actual landing point.", Category.UTILITY);
        }

        private void showMap() {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new com.zlorbie788.zeus.ui.GoliathMapScreen(mc.screen, region::set));
        }

        private List<String> locationLines() {
            if (currentSquare < 0) return List.of("Outside the mapped 450,000 × 450,000 block grid.", "Coordinates are local client coordinates; dimension must match Donut SMP.");
            Minecraft mc = Minecraft.getInstance();
            int r = GoliathGrid.region(currentSquare);
            List<String> out = new ArrayList<>();
            out.add(GoliathGrid.label(currentSquare) + (r < 0 ? "" : " · " + GoliathGrid.REGIONS[r]));
            int cells = GoliathGrid.cells(currentSquare);
            if (cells > 0) out.add(cells * 12500L / 1000L + "k × " + cells * 12500L / 1000L + " block area, " + GoliathGrid.cells(currentSquare) + " grid cell" + (cells == 1 ? "" : "s"));
            double[] area = GoliathGrid.blockArea(currentSquare);
            if (area != null) {
                out.add(String.format(java.util.Locale.ROOT, "Area %d, %d to %d, %d", (long) area[0], (long) area[1], (long) area[2], (long) area[3]));
            }
            if (mc.player != null) {
                double[] e = GoliathGrid.edges(currentSquare, mc.player.getX(), mc.player.getZ());
                if (e != null) {
                    out.add(String.format(java.util.Locale.ROOT, "To the edge: %d W, %d E, %d N, %d S", Math.round(e[0]), Math.round(e[1]), Math.round(e[2]), Math.round(e[3])));
                }
            }
            int[] next = GoliathGrid.neighbours(currentSquare);
            if (next.length > 0) {
                StringBuilder b = new StringBuilder("Next door: ");
                for (int i = 0; i < Math.min(4, next.length); i++) {
                    if (i > 0) b.append(", ");
                    b.append(GoliathGrid.label(next[i]));
                }
                out.add(b.toString());
            }
            MediaBaseFinder media = com.zlorbie788.zeus.core.Zeus.get(MediaBaseFinder.class);
            if (media != null && media.enabled()) out.add("Media Base Finder is running, so a camera base in this Goliath is reported on its own card.");
            return out;
        }

        @Override protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) { currentSquare = -1; return; }
            currentSquare = GoliathGrid.at(mc.player.getX(), mc.player.getZ());
        }

        private void request() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null || Env.local()) {
                status = "Join the target multiplayer server first";
                com.zlorbie788.zeus.core.Zeus.toast(status, false);
                return;
            }
            String cmd = command.get().trim().replaceFirst("^/", "");
            String suffix = switch (region.index()) { case 0 -> " eu central"; case 1 -> " eu west"; case 3 -> " west"; case 4 -> " east"; case 5 -> " asia"; case 6 -> " oceania"; default -> ""; };
            cmd += suffix;
            if (cmd.isBlank() || cmd.length() > 64) {
                status = "Enter a single command name";
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastRequest < 5000) {
                status = "Wait before requesting RTP again";
                return;
            }
            mc.getConnection().sendCommand(cmd);
            lastRequest = now;
            status = "Requested " + cmd + " · " + region.value();
            com.zlorbie788.zeus.core.Zeus.toast("RTP request sent", true);
        }

        @Override public String info() { return status; }
    }

    public static final class MultiAura extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "Maximum target distance.", 3.8, 2.5, 5.5, 0.1, " blocks"));
        private final Setting.Num targets = add(new Setting.Num("Targets per cycle", "Maximum number of nearby targets attacked per cycle.", 3, 1, 5, 1, ""));
        private final Setting.Num delay = add(new Setting.Num("Cycle delay", "Ticks between target cycles.", 8, 2, 30, 1, " ticks"));
        private final Setting.Num fov = add(new Setting.Num("Field of view", "Only hit targets inside this cone around your crosshair. 360 ignores it.", 160, 30, 360, 10, "\u00b0"));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while using an item", "Do not hit while you eat or draw a bow.", true));
        private final Setting.Bool hostiles = add(new Setting.Bool("Hostile mobs", "Include hostile mobs.", true));
        private final Setting.Bool players = add(new Setting.Bool("Players", "Include other players. Friends are always spared.", false));
        private int cooldown;
        private int lastCount;
        private final List<LivingEntity> queue = new ArrayList<>();

        public MultiAura() {
            super("multiaura", "MultiAura", "Hits several nearby targets in one go, nearest first, using the ordinary attack call.", Category.COMBAT);
        }

        @Override protected void onTick(Minecraft mc) {
            lastCount = 0;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (pauseUse.on() && mc.player.isUsingItem()) return;
            if (!queue.isEmpty()) {
                strike(mc, queue.remove(0));
                return;
            }
            if (cooldown-- > 0) return;
            var self = mc.player;
            double r = range.get();
            net.minecraft.world.phys.Vec3 look = self.getLookAngle();
            double cone = Math.cos(Math.toRadians(Math.min(360.0, fov.get()) / 2.0));
            List<LivingEntity> candidates = new ArrayList<>();
            for (Entity entity : mc.level.getEntities((Entity) null, new AABB(self.getX()-r,self.getY()-r,self.getZ()-r,self.getX()+r,self.getY()+r,self.getZ()+r))) {
                if (!(entity instanceof LivingEntity living) || living == self || !living.isAlive() || living.isSpectator()) continue;
                if (living instanceof TamableAnimal pet && pet.isTame()) continue;
                boolean allowed = (hostiles.on() && living instanceof Enemy) || (players.on() && living instanceof Player && !(living instanceof net.minecraft.client.player.LocalPlayer) && !Friends.protects(living));
                if (!allowed || self.distanceToSqr(living) > r*r) continue;
                if (!self.hasLineOfSight(living)) continue;
                if (fov.get() < 359.0) {
                    var to = living.getBoundingBox().getCenter().subtract(self.getEyePosition());
                    if (to.lengthSqr() > 1e-6 && to.normalize().dot(look) < cone) continue;
                }
                candidates.add(living);
            }
            candidates.sort(Comparator.comparingDouble(self::distanceToSqr));
            // One arm swings once per tick, so the cycle is spread over the next few ticks instead
            // of sending several attack packets inside a single tick.
            for (int i = 0; i < Math.min(targets.i(), candidates.size()); i++) {
                LivingEntity pick = candidates.get(i);
                if (Hits.cold(pick.getId())) continue;
                queue.add(pick);
            }
            if (queue.isEmpty()) return;
            cooldown = delay.i();
            strike(mc, queue.remove(0));
        }

        private void strike(Minecraft mc, LivingEntity target) {
            if (mc.player == null || target == null || !target.isAlive()) return;
            double r = range.get();
            if (mc.player.distanceToSqr(target) > r * r) return;
            if (Hits.cold(target.getId())) return;
            Hits.attempt(target);
            Aim.look(target.position().add(0, target.getBbHeight() * 0.55, 0));
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            lastCount = 1;
        }

        @Override protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        }

        @Override public String info() { return lastCount + " target(s)"; }
    }

    public static final class SchematicLibrary extends Module {
        private final Setting.Info library = add(new Setting.Info("Schematic files", this::files));
        private final Setting.Action openPreview = add(new Setting.Action("Open schematic library / preview", "Imports Sponge and Litematica NBT, previews blocks and calculates a material list.", this::preview));
        private final Setting.Action openFolder = add(new Setting.Action("Open Zeus schematics folder", "Opens config/zeus/schematics. The game-directory schematics folder used by Litematica is scanned too.", this::open));
        private final Setting.Action rescan = add(new Setting.Action("Rescan library", "Refreshes the supported schematic files.", this::scan));
        private final List<Path> files = new ArrayList<>();
        private Path folder, litematicaFolder;

        public SchematicLibrary() {
            super("schematiclibrary", "Schematic Library", "Imports .litematic and Sponge .schem files, provides an isometric preview and calculates required materials.", Category.UTILITY);
            scan();
        }

        private void scan() {
            try {
                folder = FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("schematics");
                litematicaFolder = FabricLoader.getInstance().getGameDir().resolve("schematics");
                Files.createDirectories(folder);
                files.clear();
                for (Path root : List.of(folder, litematicaFolder)) {
                    if (!Files.isDirectory(root)) continue;
                    try (var stream = Files.list(root)) {
                        stream.filter(Files::isRegularFile).filter(p -> {
                            String n=p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".litematic") || n.endsWith(".schem");
                        }).forEach(files::add);
                    }
                }
                files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
            } catch (Exception e) { files.clear(); files.add(Path.of("Library error: " + e.getClass().getSimpleName())); }
        }

        private void preview() {
            scan(); Minecraft mc=Minecraft.getInstance();
            mc.setScreen(new com.zlorbie788.zeus.ui.SchematicLibraryScreen(mc.screen, folder, litematicaFolder));
        }

        private void open() {
            try { scan(); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder.toFile());
                else com.zlorbie788.zeus.core.Zeus.toast("Folder: " + folder, false);
            } catch (Exception e) { com.zlorbie788.zeus.core.Zeus.toast("Could not open schematics folder", false); }
        }

        private List<String> files() {
            List<String> out=new ArrayList<>(); out.add("Zeus: config/zeus/schematics"); out.add("Litematica: .minecraft/schematics");
            out.add(files.size()+" supported file(s) indexed");
            if(files.isEmpty())out.add("Copy .litematic or .schem files into either folder.");
            else for(int i=0;i<Math.min(16,files.size());i++)out.add(files.get(i).getFileName().toString());
            return out;
        }
        @Override public String info(){return files.size()+" schematic(s)";}
    }

    public static final class SchematicBuilder extends Module {
        private final Setting.Action openFolder = add(new Setting.Action("Open schematics folder", "Opens the folder scanned by the schematic library so you can drop .litematic and .schem files directly into it.", this::openSchematicFolder));
        private final Setting.Action setOrigin = add(new Setting.Action("Set build origin here", "The loaded schematic's minimum corner will be placed at your current block position.", this::anchor));
        private final Setting.Bool automatic = add(new Setting.Bool("Auto-place", "Places available blocks by itself, using normal game placement calls, when you are within reach.", false));
        private final Setting.Bool showTarget = add(new Setting.Bool("Highlight next block", "Draw a box around the next block in the build queue.", true));
        private final Setting.Num delay = add(new Setting.Num("Placement delay", "Ticks between automatic placements.", 4, 1, 20, 1, " ticks"));
        private final Setting.Info progress = add(new Setting.Info("Build progress", this::progressLines));
        private net.minecraft.core.BlockPos origin;
        private int cursor, placed, ticks;
        private String status = "Load a schematic, then set the origin";
        private net.minecraft.core.BlockPos target;
        private final com.zlorbie788.zeus.render.EspKit kit = new com.zlorbie788.zeus.render.EspKit(this, 0, false);

        public SchematicBuilder() {
            super("schematicbuilder", "Schematic Builder", "Guided material-aware schematic building. Optional automatic placement uses ordinary block placement.", Category.UTILITY);
        }
        private void openSchematicFolder() {
            try {
                java.nio.file.Path dir=FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("schematics");
                java.nio.file.Files.createDirectories(dir);
                if(Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir.toFile());
                else com.zlorbie788.zeus.core.Zeus.toast("Schematics folder: "+dir, false);
            } catch(Exception e) { com.zlorbie788.zeus.core.Zeus.toast("Could not open schematics folder", false); }
        }
        private void anchor() {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null||SchematicData.lastLoaded==null){status="Load a schematic first";return;}
            origin=mc.player.blockPosition();cursor=0;placed=0;target=null;status="Origin set at "+origin.getX()+" "+origin.getY()+" "+origin.getZ();
        }
        private List<String> progressLines() {
            SchematicData d=SchematicData.lastLoaded;
            if(d==null)return List.of("No schematic imported yet.","Open Schematic Library / Preview first.");
            return List.of(status, "Progress: "+placed+" / "+d.cells.size(), origin==null?"Set the build origin to begin.":"Origin: "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
        }
        @Override protected void onTick(Minecraft mc) {
            SchematicData d=SchematicData.lastLoaded;
            if(d==null||origin==null||mc.level==null||mc.player==null)return;
            if(cursor>=d.cells.size()){status="Build queue complete";target=null;return;}
            if(++ticks%Math.max(1,delay.i())!=0)return;
            int inspected=0;
            while(cursor<d.cells.size()&&inspected++<160){
                SchematicData.Cell c=d.cells.get(cursor);
                net.minecraft.core.BlockPos pos=origin.offset(c.x(),c.y(),c.z());
                net.minecraft.resources.Identifier id;
                try {String state=c.state();int prop=state.indexOf('[');if(prop>=0)state=state.substring(0,prop);int colon=state.indexOf(':');id=colon<0?net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft",state):net.minecraft.resources.Identifier.fromNamespaceAndPath(state.substring(0,colon),state.substring(colon+1));}
                catch(Exception e){cursor++;continue;}
                net.minecraft.world.level.block.Block block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
                if(block==net.minecraft.world.level.block.Blocks.AIR){cursor++;continue;}
                if(mc.level.getBlockState(pos).is(block)){cursor++;placed++;continue;}
                target=pos;
                net.minecraft.world.item.Item item=block.asItem();
                if(item==net.minecraft.world.item.Items.AIR){status="No placeable item for "+c.state();return;}
                int slot=com.zlorbie788.zeus.core.Inv.hotbar(st->st.is(item));
                if(slot<0){int main=com.zlorbie788.zeus.core.Inv.main(st->st.is(item));
                    if(main>=0&&automatic.on()){com.zlorbie788.zeus.core.Inv.click(com.zlorbie788.zeus.core.Inv.menuSlot(main),com.zlorbie788.zeus.core.Inv.selected(),net.minecraft.world.inventory.ClickType.SWAP);slot=com.zlorbie788.zeus.core.Inv.selected();}
                }
                if(slot<0){status="Waiting for material: "+c.state()+" (missing from hotbar)";return;}
                if(!automatic.on()){status="Ready: "+c.state()+" at "+pos.getX()+" "+pos.getY()+" "+pos.getZ();return;}
                if(!mc.player.onGround()||mc.player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>Build.REACH*Build.REACH){status="Move within reach of "+pos.getX()+" "+pos.getY()+" "+pos.getZ();return;}
                if(!Build.canPlace(pos,Build.REACH)){status="Waiting for a clear target and supporting face";return;}
                if(Build.place(pos,com.zlorbie788.zeus.core.Inv.is(item),true,Build.REACH)){cursor++;placed++;status="Placed "+c.state()+(c.state().contains("[")?" · verify orientation":"");}
                return;
            }
            if(inspected>=160)status="Scanning build queue…";
        }
        @Override protected void onHud(GuiGraphics g,Minecraft mc,float partial,int w,int h){
            if(target==null||!showTarget.on()||mc.level==null)return;
            if(com.zlorbie788.zeus.render.Projector.prepare(mc,partial,w,h))kit.box(g,target.getX(),target.getY(),target.getZ(),target.getX()+1,target.getY()+1,target.getZ()+1,0xFF63E6A2,1f,true);
            g.drawString(mc.font,"Schematic: "+status,8,h-30,0xFF9FE8BE,true);
        }
        @Override public String info(){return status;}
    }

    public static final class FakeLagMonitor extends Module {
        private final Setting.Bool overlay = add(new Setting.Bool("Show latency overlay", "Show measured ping, rolling average and jitter.", true));
        private final Setting.Num simulated = add(new Setting.Num("Simulated latency", "Display-only latency for testing HUD layouts. Does not delay packets.", 250, 0, 2000, 25, " ms"));
        private final java.util.ArrayDeque<Integer> samples = new java.util.ArrayDeque<>();
        private int measured=-1, average=-1, jitter=-1, ticks;
        public FakeLagMonitor(){super("lagmonitor","Fake Lag Monitor","Rolling ping and jitter monitor with a display-only latency simulator. It reads your real ping and never touches a packet; the Fake Lag module is the one that delays them.",Category.UTILITY);alias("fake lag monitor","ping monitor","latency");hudCard();}
        @Override protected void onTick(Minecraft mc){
            if(mc.player==null||mc.getConnection()==null){measured=average=jitter=-1;samples.clear();return;}
            if(++ticks%20!=0)return;
            var info=mc.getConnection().getPlayerInfo(mc.player.getUUID()); measured=info==null?-1:info.getLatency();
            if(measured>=0){samples.addLast(measured);while(samples.size()>30)samples.removeFirst();int sum=0;for(int v:samples)sum+=v;average=sum/samples.size();
                if(samples.size()>1){int d=0,prev=-1;for(int v:samples){if(prev>=0)d+=Math.abs(v-prev);prev=v;}jitter=d/(samples.size()-1);}else jitter=0;}
        }
        @Override protected void onHud(GuiGraphics g,Minecraft mc,float partial,int w,int h){
            if(!overlay.on()||mc.player==null)return;
            String t="Ping "+(measured<0?"—":measured+" ms")+"  Avg "+(average<0?"—":average)+"  Jit "+(jitter<0?"—":jitter)+"  Sim "+simulated.i();
            Hud.record(6,6,6+mc.font.width(t),16);g.fill(6,6,12+mc.font.width(t),22,0xA90A111E);g.drawString(mc.font,t,9,10,0xFFE6EAF2,true);
        }
        @Override public String info(){return measured<0?"No ping":measured+" ms · avg "+average+" · jitter "+jitter;}
    }

}
