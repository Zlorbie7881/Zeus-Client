package com.zlorbie788.zeus.mods;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded reader for Sponge .schem and Litematica .litematic NBT files. No external parser dependency. */
public final class SchematicData {
    public static volatile SchematicData lastLoaded;
    public record Cell(int x, int y, int z, String state) {}
    public final String name;
    public final int width, height, length;
    public final List<Cell> cells;
    public final Map<String, Integer> materials;

    private SchematicData(String name, int width, int height, int length, List<Cell> cells) {
        this.name = name; this.width = width; this.height = height; this.length = length; this.cells = List.copyOf(cells);
        Map<String, Integer> counts = new TreeMap<>();
        for (Cell c : cells) { String id=baseState(c.state); if (!id.equals("minecraft:air") && !id.equals("minecraft:cave_air") && !id.equals("minecraft:void_air")) counts.merge(id, 1, Integer::sum); }
        this.materials = Collections.unmodifiableMap(counts);
    }

    public static SchematicData read(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > 64L * 1024 * 1024) throw new IOException("Schematic must be between 1 byte and 64 MB");
        byte[] bytes = Files.readAllBytes(path);
        InputStream raw = new ByteArrayInputStream(bytes);
        if (bytes.length > 2 && (bytes[0] & 255) == 0x1f && (bytes[1] & 255) == 0x8b) raw = new GZIPInputStream(raw);
        Object root;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            int type = in.readUnsignedByte();
            if (type != 10) throw new IOException("File does not contain a root NBT compound");
            in.readUTF();
            root = readPayload(in, 10, 0);
        } catch (EOFException e) { throw new IOException("Schematic NBT is truncated", e); }
        if (!(root instanceof Map<?, ?> map)) throw new IOException("Invalid NBT root");
        Map<String, Object> nbt = cast(map);
        SchematicData parsed = nbt.containsKey("Regions") ? readLitematic(path.getFileName().toString(), nbt) : readSponge(path.getFileName().toString(), nbt);
        lastLoaded = parsed;
        return parsed;
    }

    private static SchematicData readSponge(String name, Map<String,Object> root) throws IOException {
        Map<String,Object> d = root.get("Schematic") instanceof Map<?,?> m ? cast(m) : root;
        int w = number(d.get("Width")), h = number(d.get("Height")), l = number(d.get("Length"));
        checkVolume(w,h,l);
        Object paletteObj = d.get("Palette");
        if (!(paletteObj instanceof Map<?,?>)) throw new IOException("Unsupported schematic: no Sponge Palette");
        Map<Integer,String> palette = new HashMap<>();
        cast((Map<?,?>)paletteObj).forEach((k,v) -> palette.put(number(v), normalizeState(String.valueOf(k))));
        Object dataObj = d.get("BlockData");
        if (!(dataObj instanceof byte[] data)) throw new IOException("Unsupported schematic: no BlockData");
        List<Cell> cells = new ArrayList<>(); int[] cursor = {0}; int total = Math.min(w*h*l, 1_000_000);
        for (int i=0; i<total && cursor[0]<data.length; i++) {
            int id = readVarInt(data, cursor);
            String state = palette.get(id);
            if (state == null || state.endsWith(":air") || state.endsWith(":cave_air") || state.endsWith(":void_air")) continue;
            int x = i % w, z = (i / w) % l, y = i / (w*l);
            cells.add(new Cell(x,y,z,state));
        }
        if (cells.isEmpty()) throw new IOException("No non-air blocks found in schematic");
        return new SchematicData(name,w,h,l,cells);
    }

    private static SchematicData readLitematic(String name, Map<String,Object> root) throws IOException {
        Object ro = root.get("Regions");
        if (!(ro instanceof Map<?,?> regions)) throw new IOException("Litematic has no Regions compound");
        List<Cell> cells = new ArrayList<>();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        record RegionData(Map<String,Object> data,int sx,int sy,int sz,int px,int py,int pz) {}
        List<RegionData> parsed = new ArrayList<>();
        for (Object value : regions.values()) {
            if (!(value instanceof Map<?,?> rm)) continue;
            Map<String,Object> r = cast(rm);
            Map<String,Object> size = child(r,"Size"), pos = child(r,"Position");
            int sx=Math.abs(number(size.get("x"))), sy=Math.abs(number(size.get("y"))), sz=Math.abs(number(size.get("z")));
            int px=number(pos.get("x"))-(number(size.get("x"))<0?sx-1:0), py=number(pos.get("y"))-(number(size.get("y"))<0?sy-1:0), pz=number(pos.get("z"))-(number(size.get("z"))<0?sz-1:0);
            checkVolume(sx,sy,sz);
            minX=Math.min(minX,px); minY=Math.min(minY,py); minZ=Math.min(minZ,pz);
            parsed.add(new RegionData(r,sx,sy,sz,px,py,pz));
        }
        int maxX=0,maxY=0,maxZ=0;
        for (RegionData rd:parsed) {
            Map<String,Object> r=rd.data();
            Object po=r.get("BlockStatePalette"), bo=r.get("BlockStates");
            if (!(po instanceof List<?> paletteList) || !(bo instanceof long[] states)) continue;
            List<String> palette=new ArrayList<>();
            for (Object p:paletteList) {
                if (p instanceof Map<?,?> pm) {
                    Map<String,Object> state=cast(pm); String block=String.valueOf(state.getOrDefault("Name","minecraft:air"));
                    Object props=state.get("Properties");
                    if (props instanceof Map<?,?> prop && !prop.isEmpty()) {
                        List<String> pairs=new ArrayList<>();
                        for (Map.Entry<?,?> entry:prop.entrySet()) pairs.add(entry.getKey()+"="+entry.getValue());
                        Collections.sort(pairs); block += "["+String.join(",",pairs)+"]";
                    }
                    palette.add(normalizeState(block));
                } else palette.add("minecraft:air");
            }
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(Math.max(1,palette.size()-1)));
            int volume=rd.sx()*rd.sy()*rd.sz();
            for (int i=0;i<volume && cells.size()<1_000_000;i++) {
                int id=packed(states,i,bits); if (id<0 || id>=palette.size()) continue;
                String state=palette.get(id); if (state.endsWith(":air") || state.endsWith(":cave_air") || state.endsWith(":void_air")) continue;
                int x=i%rd.sx(), z=(i/rd.sx())%rd.sz(), y=i/(rd.sx()*rd.sz());
                int ax=rd.px()+x-minX, ay=rd.py()+y-minY, az=rd.pz()+z-minZ;
                cells.add(new Cell(ax,ay,az,state)); maxX=Math.max(maxX,ax+1); maxY=Math.max(maxY,ay+1); maxZ=Math.max(maxZ,az+1);
            }
        }
        if (cells.isEmpty()) throw new IOException("No readable blocks found in Litematic regions");
        checkVolume(maxX,maxY,maxZ);
        return new SchematicData(name,maxX,maxY,maxZ,cells);
    }

    private static int packed(long[] data,int index,int bits) {
        long bit=(long)index*bits; int word=(int)(bit>>>6), offset=(int)(bit&63);
        if(word>=data.length)return -1;
        long value=data[word]>>>offset;
        if(offset+bits>64 && word+1<data.length)value|=data[word+1]<<(64-offset);
        return (int)(value & ((1L<<bits)-1));
    }
    private static int readVarInt(byte[] data,int[] p) throws IOException {
        int result=0,shift=0;
        while(p[0]<data.length && shift<35){int b=data[p[0]++]&255;result|=(b&127)<<shift;if((b&128)==0)return result;shift+=7;}
        throw new IOException("Invalid or truncated block palette data");
    }
    private static String baseState(String s) { int p=s.indexOf('['); return p<0?s:s.substring(0,p); }
    private static String normalizeState(String s) { return s.replace(" ","").toLowerCase(Locale.ROOT).contains(":")?s.replace(" ","").toLowerCase(Locale.ROOT):"minecraft:"+s.replace(" ","").toLowerCase(Locale.ROOT); }
    private static void checkVolume(int w,int h,int l) throws IOException { if(w<1||h<1||l<1||(long)w*h*l>1_000_000)throw new IOException("Schematic dimensions are invalid or exceed one million blocks"); }
    private static int number(Object o) { return o instanceof Number n?n.intValue():0; }
    private static Map<String,Object> child(Map<String,Object> m,String key) throws IOException { Object o=m.get(key); if(o instanceof Map<?,?> x)return cast(x); throw new IOException("Missing NBT compound: "+key); }
    @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?> m) { return (Map<String,Object>)m; }

    private static Object readPayload(DataInputStream in,int type,int depth) throws IOException {
        if(depth>64)throw new IOException("NBT nesting too deep");
        return switch(type) {
            case 1 -> in.readByte(); case 2 -> in.readShort(); case 3 -> in.readInt(); case 4 -> in.readLong();
            case 5 -> in.readFloat(); case 6 -> in.readDouble();
            case 7 -> {int n=in.readInt();if(n<0||n>64*1024*1024)throw new IOException("Invalid NBT byte array");byte[] a=new byte[n];in.readFully(a);yield a;}
            case 8 -> in.readUTF();
            case 9 -> {int t=in.readUnsignedByte(),n=in.readInt();if(n<0||n>2_000_000)throw new IOException("Invalid NBT list");List<Object>a=new ArrayList<>(Math.min(n,100_000));for(int i=0;i<n;i++)a.add(readPayload(in,t,depth+1));yield a;}
            case 10 -> {Map<String,Object>m=new LinkedHashMap<>();while(true){int t=in.readUnsignedByte();if(t==0)break;String k=in.readUTF();m.put(k,readPayload(in,t,depth+1));if(m.size()>200_000)throw new IOException("NBT compound too large");}yield m;}
            case 11 -> {int n=in.readInt();if(n<0||n>2_000_000)throw new IOException("Invalid NBT int array");int[]a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();yield a;}
            case 12 -> {int n=in.readInt();if(n<0||n>2_000_000)throw new IOException("Invalid NBT long array");long[]a=new long[n];for(int i=0;i<n;i++)a[i]=in.readLong();yield a;}
            default -> throw new IOException("Unknown NBT tag type "+type);
        };
    }
}
