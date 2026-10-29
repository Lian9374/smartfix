import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
/** Persistent signer avoids launching a JVM per request; credentials stay on stdin/stdout pipes. */
class PerfTokenSigner {
 public static void main(String[] a)throws Exception {
  var key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(a[0]))));
  var b=Base64.getUrlEncoder().withoutPadding();var in=new Scanner(System.in);
  while(in.hasNextLine()) {
   String payload=in.nextLine();String content=b.encodeToString("{\"alg\":\"RS256\",\"kid\":\"stage4-core-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+b.encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var sig=Signature.getInstance("SHA256withRSA");sig.initSign(key);sig.update(content.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
   System.out.println(content+"."+b.encodeToString(sig.sign()));System.out.flush();
  }
 }
}
