package dev.shirokai.daily.core;
import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
/** Owned storage executor only. A failure before replacement preserves the previous snapshot. */
public final class Store {
    private final Path root;
    private final boolean syncDirectory;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public Store(Path root)throws IOException{
        this(root,!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"));
    }
    // Windows FileChannel cannot open directories. Never suppress errors from actual file writes.
    Store(Path root,boolean syncDirectory)throws IOException{
        this.root=root;this.syncDirectory=syncDirectory;Files.createDirectories(root);
    }
    public State load(UUID id)throws IOException{
        Path p=root.resolve(id+".json");if(!Files.exists(p))return new State();
        try(var reader=Files.newBufferedReader(p)){State s=JSON.fromJson(reader,State.class);if(s==null||s.schema!=1||s.daily==null||s.playtime==null||s.hunts==null||s.transactions==null)throw new IOException("Estado inválido: "+id);return s;}
        catch(JsonParseException e){throw new IOException("JSON corrompido, preservado: "+id,e);}
    }
    public void save(UUID id,State state)throws IOException{
        Path target=root.resolve(id+".json"),tmp=root.resolve(id+".tmp");
        write(target,tmp,state);
    }
    public SourceExclusions loadExclusions()throws IOException{
        Path p=root.resolve("excluded-raid-sources.json");if(!Files.exists(p))return new SourceExclusions();
        try(var reader=Files.newBufferedReader(p)){
            SourceExclusions value=JSON.fromJson(reader,SourceExclusions.class);
            if(value==null||value.schema!=1||value.battles==null||value.pokemon==null)throw new IOException("Invalid Raid exclusion journal");return value;
        }catch(JsonParseException e){throw new IOException("Corrupt Raid exclusion journal preserved",e);}
    }
    public void saveExclusions(SourceExclusions data)throws IOException{write(root.resolve("excluded-raid-sources.json"),root.resolve("excluded-raid-sources.tmp"),data);}
    private void write(Path target,Path tmp,Object data)throws IOException{
        byte[] bytes=JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}
        Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        // File contents are forced on every platform. Directory fsync is an additional Unix barrier;
        // the Windows provider rejects opening directories even when the JSON write succeeded.
        if(syncDirectory)try(FileChannel dir=FileChannel.open(root,StandardOpenOption.READ)){dir.force(true);}
    }
}
