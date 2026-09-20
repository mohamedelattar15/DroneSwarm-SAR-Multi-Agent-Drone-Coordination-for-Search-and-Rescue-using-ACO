package agents;

import agents.protocol.MessageProtocol;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent représentant une victime à secourir.
 * Confirme les détections des drones.
 */
public class VictimAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(VictimAgent.class);
    public static final String SERVICE_NAME = MessageProtocol.SERVICE_VICTIM;
    public static final String ONTOLOGY_VICTIM_DETECTED = MessageProtocol.ONTOLOGY_VICTIM_DETECTED;
    public static final String ONTOLOGY_VICTIM_CONFIRMED = MessageProtocol.ONTOLOGY_VICTIM_CONFIRMED;

    private int detectionCount = 0;

    @Override
    protected void setup() {
        registerWithDF();
        log.debug("Victime prête à être localisée");
        addBehaviour(new HandleDetection());
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_NAME);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) {
            log.error("Victime: Échec DF", e);
        }
    }

    private class HandleDetection extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology(ONTOLOGY_VICTIM_DETECTED));

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg == null) { block(); return; }

            detectionCount++;
            log.info("🆘 Drone {} m'a détecté! (détection #{})", msg.getSender().getLocalName(), detectionCount);

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setOntology(ONTOLOGY_VICTIM_CONFIRMED);
            reply.setContent(MessageProtocol.VICTIM_CONFIRMED + ":" + detectionCount);
            myAgent.send(reply);
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        log.debug("Victime secourue!");
    }
}
