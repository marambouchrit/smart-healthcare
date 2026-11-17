// Valide des fichiers XML contre un XSD avec la bibliothèque standard Java (aucune dépendance).
// Usage : java scripts/ValidateXsd.java <schema.xsd> <fichier.xml>...
// Code de sortie : 0 si tous les fichiers sont valides, 1 sinon.
import java.io.File;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXParseException;

public class ValidateXsd {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage : java scripts/ValidateXsd.java <schema.xsd> <fichier.xml>...");
            System.exit(2);
        }
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema;
        try {
            schema = factory.newSchema(new File(args[0]));
            System.out.println("SCHEMA OK  " + args[0]);
        } catch (SAXParseException e) {
            System.out.println("SCHEMA KO  " + args[0] + " (ligne " + e.getLineNumber() + ") : " + e.getMessage());
            System.exit(1);
            return;
        }
        int erreurs = 0;
        for (int i = 1; i < args.length; i++) {
            try {
                schema.newValidator().validate(new StreamSource(new File(args[i])));
                System.out.println("VALIDE     " + args[i]);
            } catch (SAXParseException e) {
                erreurs++;
                System.out.println("INVALIDE   " + args[i] + " (ligne " + e.getLineNumber() + ") : " + e.getMessage());
            }
        }
        System.exit(erreurs == 0 ? 0 : 1);
    }
}
