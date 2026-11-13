package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import org.junit.jupiter.api.Test;

class JavaRuntimeTest {
  @Test
  void buildAndTestsUseJava25Bytecode() throws Exception {
    assertEquals(25, Runtime.version().feature(), "Run the build with JDK 25");
    try (var bytes =
        new DataInputStream(
            SyneraApplication.class.getResourceAsStream("SyneraApplication.class"))) {
      assertEquals(0xCAFEBABE, bytes.readInt());
      bytes.readUnsignedShort();
      assertEquals(69, bytes.readUnsignedShort(), "Maven compiler must target Java 25");
    }
  }
}
