package tn.ginf.smarthealthcare.soap;

import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publie le service sur /ws/dossier (cxf.path=/ws dans application.yml). */
@Configuration
public class CxfConfig {

    @Bean
    public Endpoint dossierEndpoint(Bus bus, DossierServiceImpl implementation) {
        EndpointImpl endpoint = new EndpointImpl(bus, implementation);
        endpoint.setWsdlLocation("classpath:wsdl/dossier.wsdl");
        // Les messages entrants et sortants sont validés contre dossier.xsd (format TN-xxxxxxx, énumérations…)
        endpoint.getProperties().put("schema-validation-enabled", "true");
        endpoint.publish("/dossier");
        return endpoint;
    }
}
