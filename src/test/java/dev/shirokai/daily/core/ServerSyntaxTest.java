package dev.shirokai.daily.core;
import com.github.javaparser.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
/** Syntax only. Does NOT validate Minecraft mappings or Fabric/Cobblemon linkage. */
class ServerSyntaxTest {
 @Test void serverSourcesParseAsJava21()throws Exception{
  var parser=new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
  try(var paths=Files.walk(Path.of("server/src/main/java"))){
   var sources=paths.filter(p->p.toString().endsWith(".java")).toList();assertFalse(sources.isEmpty());
   for(Path source:sources){var result=parser.parse(source);assertTrue(result.isSuccessful(),source+": "+result.getProblems());}
  }
 }
}
