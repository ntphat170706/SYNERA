package vn.synera;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import vn.synera.common.BusinessException;
import vn.synera.integration.*;

class IntegrationSecurityTest {
  @Test
  void callbackBindsTimeEventPathAndBody() throws Exception {
    String secret = "test-callback-key-with-at-least-32-characters",
        time = Long.toString(Instant.now().getEpochSecond()),
        event = "event-1",
        path = "/internal/display-audits/1/result",
        body = "{\"score\":80}";
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String proof =
        HexFormat.of()
            .formatHex(
                mac.doFinal(
                    (time + "." + event + "." + path + "." + body)
                        .getBytes(StandardCharsets.UTF_8)));
    var verifier = new CallbackSignature(secret);
    assertDoesNotThrow(() -> verifier.verify(time, event, path, body, proof));
    assertThrows(
        BusinessException.class, () -> verifier.verify(time, event, path, body + " ", proof));
    assertThrows(BusinessException.class, () -> verifier.verify("0", event, path, body, proof));
    assertThrows(
        BusinessException.class, () -> verifier.verify(time, "event-2", path, body, proof));
  }

  @Test
  void scannerStreamsFramedBytesAndRejectsDetectedThreats() throws Exception {
    for (boolean clean : new boolean[] {true, false})
      try (var server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
        byte[] bytes = "harmless test bytes".getBytes(StandardCharsets.UTF_8);
        var received = new java.util.concurrent.CompletableFuture<byte[]>();
        var thread =
            Thread.ofPlatform()
                .start(
                    () -> {
                      try (var socket = server.accept()) {
                        var input = new DataInputStream(socket.getInputStream());
                        assertEquals(
                            "zINSTREAM\0",
                            new String(input.readNBytes(10), StandardCharsets.US_ASCII));
                        var output = new ByteArrayOutputStream();
                        for (int size; (size = input.readInt()) != 0; )
                          output.write(input.readNBytes(size));
                        received.complete(output.toByteArray());
                        socket
                            .getOutputStream()
                            .write(
                                (clean ? "stream: OK\0" : "stream: TestSignature FOUND\0")
                                    .getBytes(StandardCharsets.UTF_8));
                      } catch (Exception e) {
                        received.completeExceptionally(e);
                      }
                    });
        assertEquals(clean, new MalwareScanner("127.0.0.1", server.getLocalPort()).clean(bytes));
        assertArrayEquals(bytes, received.get(2, java.util.concurrent.TimeUnit.SECONDS));
        thread.join(2000);
      }
  }
}
