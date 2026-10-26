import java.nio.file.*;
import java.security.*;
import java.util.Base64;

/** Offline signer. The portable encrypted keystore and password stay outside Git. */
class SignUpdateManifest {
    public static void main(String[] args) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        var password = System.getenv("LUMEN_UPDATE_PASSWORD").toCharArray();
        try (var input = Files.newInputStream(Path.of(System.getenv("LUMEN_UPDATE_KEYSTORE")))) {
            store.load(input, password);
        }
        String alias = "lumen-update-20261026";
        if (args[0].equals("public")) {
            System.out.println(Base64.getEncoder().encodeToString(store.getCertificate(alias).getPublicKey().getEncoded()));
        } else {
            var signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign((PrivateKey) store.getKey(alias, password));
            signer.update(System.in.readAllBytes());
            System.out.println(Base64.getEncoder().encodeToString(signer.sign()));
        }
        java.util.Arrays.fill(password, '\0');
    }
}
