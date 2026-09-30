package ru.daniel.soviethifi.storage;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TrackStore {
    public record Meta(String id, String title, String format, int size, int seconds) {}
    private final Path root;
    private final Map<String,Meta> tracks = new HashMap<>();
    private long used;
    public TrackStore(Path root) throws IOException {
        this.root = root; Files.createDirectories(root);
        try (var files = Files.list(root)) {
            for(Path file:files.filter(p -> p.getFileName().toString().endsWith(".properties")).toList()) {
                try (Reader reader=Files.newBufferedReader(file)) {
                    Properties p=new Properties(); p.load(reader); String id=p.getProperty("id");
                    if(!TrackRules.validId(id))continue;
                    int size=Integer.parseInt(p.getProperty("size")), seconds=Integer.parseInt(p.getProperty("seconds"));
                    String format=p.getProperty("format");
                    if(size<32||size>TrackRules.MAX_BYTES||seconds<1||seconds>TrackRules.MAX_SECONDS||!(format.equals("mp3")||format.equals("ogg")))continue;
                    if(Files.size(audio(id))!=size)continue;
                    Meta meta=new Meta(id,TrackRules.title(p.getProperty("title","Кассета")),format,size,seconds);
                    tracks.put(id,meta); used+=size;
                } catch(Exception ignored) { /* An interrupted or corrupt entry does not disable the world. */ }
            }
        }
    }
    private Path audio(String id) { if(!TrackRules.validId(id))throw new IllegalArgumentException("Invalid ID"); return root.resolve(id+".audio"); }
    public synchronized Meta get(String id) { return tracks.get(id); }
    public synchronized long used() { return used; }
    public synchronized byte[] read(String id) throws IOException {
        Meta meta=tracks.get(id); if(meta==null)throw new IOException("Трек отсутствует на сервере");
        if(Files.size(audio(id))!=meta.size)throw new IOException("Размер файла изменился");
        return Files.readAllBytes(audio(id));
    }
    public synchronized Meta put(Meta meta, byte[] bytes) throws IOException {
        if(tracks.containsKey(meta.id))return tracks.get(meta.id);
        if(used+bytes.length>TrackRules.LIBRARY_BYTES)throw new IOException("Хранилище музыки заполнено (256 МБ)");
        if(bytes.length!=meta.size||!TrackRules.hash(bytes).equals(meta.id)||!TrackRules.audioHeader(bytes,meta.format))throw new IOException("Некорректный аудиофайл");
        Path temporary=root.resolve(meta.id+".part"); Files.write(temporary,bytes);
        Files.move(temporary,audio(meta.id),StandardCopyOption.REPLACE_EXISTING);
        Properties p=new Properties(); p.setProperty("id",meta.id); p.setProperty("title",meta.title); p.setProperty("format",meta.format); p.setProperty("size",Integer.toString(meta.size)); p.setProperty("seconds",Integer.toString(meta.seconds));
        try(Writer out=Files.newBufferedWriter(root.resolve(meta.id+".properties"))){p.store(out,"Soviet Hi-Fi track");}
        tracks.put(meta.id,meta); used+=bytes.length; return meta;
    }
}
