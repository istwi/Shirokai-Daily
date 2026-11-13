package dev.shirokai.daily.core;
import java.nio.file.Path;
/** Generate examples through the same path as first server startup. Never overwrite existing files. */
public final class Defaults {
 public static void main(String[] args)throws Exception{Config.load(Path.of(args.length==0?"examples/config/shirokai-daily":args[0]));}
}
