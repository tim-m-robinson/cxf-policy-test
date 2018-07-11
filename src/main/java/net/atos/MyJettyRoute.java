/*
 * Copyright 2005-2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package net.atos;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;

import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.cxf.Bus;

import org.apache.cxf.ws.policy.IgnorablePolicyInterceptorProvider;
import org.apache.cxf.ws.policy.PolicyInterceptorProviderRegistry;
import org.apache.cxf.ws.security.wss4j.UsernameTokenInterceptor;
import org.w3c.dom.Document;

/**
 * Configures all our Camel routes, components, endpoints and beans
 */
@ContextName("myJettyCamel")
public class MyJettyRoute extends RouteBuilder {

    @Inject @Uri("jetty:http://0.0.0.0:1080/proxy?matchOnUriPrefix=true")
    private Endpoint jettyEndpoint;
    
    @Inject @Uri("cxf://http://localhost:8080/cxf-soap-dummy/dummyService?wsdlURL=http://localhost:8080/cxf-soap-dummy/dummyService?wsdl&defaultOperationName=reverseOperation&defaultOperationNamespace=http://net.atos&dataFormat=CXF_MESSAGE")
    private Endpoint cxfEndpoint;
    
    @Inject @Uri("http4://localhost:8080/cxf-soap-dummy/dummyService?bridgeEndpoint=true&throwExceptionOnFailure=false")
    private Endpoint httpEndpoint;
	
	@Inject
	CamelContext ctx;
	
	@Inject @Uri("timer://foo?fixedRate=true&period=5s")
	private Endpoint timer;

    @Inject @Uri("direct:in")
    private Endpoint in;
    
    @Inject @Uri("direct:out")
    private Endpoint out;
    
    @Inject @Uri("direct:foo")
    private Endpoint foo;

    private String rawXmlData =
    	"<soapenv:Envelope " +
    	"	xmlns:ns0=\"http://net.atos\" "+
    	"	xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "+
    	"	xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "+
    	"	xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "+
    	"   xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"> "+
    	"	<soapenv:Header> "+
   	
        "<wsse:Security> "+
        "<wsse:UsernameToken> "+
        "  <wsse:Username>alice</wsse:Username> "+
        "   <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">password</wsse:Password> "+
        "</wsse:UsernameToken> "+
        "</wsse:Security> "+	   	
    	
    	"	</soapenv:Header> "+
	 	"	<soapenv:Body> "+
	 	"	<ns0:reverseOperation xmlns:ns0=\"http://net.atos\"> "+
	 	"     <arg0>hello</arg0> "+
	 	"	</ns0:reverseOperation> "+
	 	"	</soapenv:Body> "+
	 	"</soapenv:Envelope>";

    @Override
    public void configure() throws Exception {
        // you can configure the route rule with Java DSL here
        //	getContext().setStreamCaching(true);
    	
    	doStuff();

        from(jettyEndpoint)
        	.to("log:?level=INFO&showAll=true&showStreams=true")    		
        	.log("*** 1: ${in.body}")
        	/*.doTry()*/
        	    .process((exchange) -> {
        	    	Map headers = exchange.getIn().getHeaders();
                    headers.values().removeIf(Objects::isNull);
                 })
        	    .setHeader("operationName")
        	    	.constant("reverseOperation")
        		//.convertBodyTo(String.class)
        		.setHeader(Exchange.HTTP_METHOD)
        			.constant("POST")
        		/* Message for dataType RAW */
        		.setBody()
        			.constant(rawXmlData)
        		/* Message for dataType PAYLOAD */
        		/*.process((exchange) -> {
        			 exchange.getIn().setBody("<arg0>hello</arg0>");
        		})*/
        		/* Message for dataType CXF_MESSAGE */
        		.process((exchange) -> {
        			MessageFactory cxfMf = MessageFactory.newInstance();
        			SOAPMessage cxfMessage = cxfMf.createMessage();
        			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        			Document document = db.parse(new ByteArrayInputStream("<arg0>hello</arg0>".getBytes("UTF-8")));
        			cxfMessage.getSOAPBody().addDocument(document);
        			exchange.getIn().setBody(cxfMessage);      			
        		})
            	//.convertBodyTo(javax.xml.transform.Source.class)
                .log("*** 2: ${in.body}")
            	.to(cxfEndpoint)
            /*.doCatch(Exception.class)
            	.setHeader(Exchange.HTTP_RESPONSE_CODE)
            		.constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
            	.transform()
            		.simple("Exception")
            .endDoTry()*/
            .to("mock:result");
        
        from(foo)
          .setBody()
          	.constant("foo");
        
        from(out).to("mock:out");
    }
    
    private void doStuff() {
    	   	
    	((CxfEndpoint) cxfEndpoint).setEndpointNameString("{http://net.atos}dummyPort");
    	((CxfEndpoint) cxfEndpoint).setServiceNameString("{http://net.atos}dummyService");
    	
    	System.setProperty("org.apache.cxf.stax.allowInsecureParser", "1");
    	 	
        Bus bus = ((CxfEndpoint) cxfEndpoint).getBus();  
        System.out.println("*** : Got Bus");
        
        new UsernameTokenInterceptor().getUnderstoodHeaders().forEach(item -> System.out.println(item.getNamespaceURI() +":"+ item.getLocalPart()));
        
        /****************/
        bus.setProperty("ws-security.username", "alice");
        bus.setProperty("ws-security.password", "password");
        //bus.setProperty("ws-security.ut.validator", null);
        /****************/
        
        
        Object o = bus.getExtension(PolicyInterceptorProviderRegistry.class);
        System.out.println("*** : " + o.getClass().getName());
        PolicyInterceptorProviderRegistry reg = bus.getExtension(PolicyInterceptorProviderRegistry.class);
        System.out.println("*** : Got Inteceptor Registry");
        
        QName qname = new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "UsernameToken");
        
        System.out.println("*** UsernameToken Inteceptors: ");
        reg.get(qname).forEach(i -> i.getOutInterceptors().forEach(j -> System.out.println(j.getClass().getName())));
        //reg.setBus(null);
        
        //reg.unregister(new QName("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Security"));
        //reg.unregister(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "UsernameToken"));
        //reg.unregister(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "SupportingTokens"));
        //reg.unregister(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "WssUsernameToken10"));

        
        Set  set = new HashSet<>();
        //set.add(new QName("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Security"));
        set.add(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "UsernameToken"));
        set.add(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "SupportingTokens"));
        set.add(new QName("http://docs.oasis-open.org/ws-sx/ws-securitypolicy/200702", "WssUsernameToken10"));
        reg.register(new IgnorablePolicyInterceptorProvider(set));
     }

}
