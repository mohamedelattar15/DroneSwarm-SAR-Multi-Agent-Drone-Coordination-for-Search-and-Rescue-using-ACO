package agents;

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
import utils.SimulationConfig;

/**
 * Agent représentant la base de secours.
 * Reçoit les drones de retour et coordonne la logistique.
 */
public class BaseAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);
    public static final String SERVICE_NAME = "BaseService";
    public static final String ONTOLOGY_DRONE_RETURNED = "DRONE_RETURNED";
    public static final String ONTOLOGY_NEW_MISSION = "NEW_MISSION";

    private SimulationConfig config;
    private int totalReturns = 0;
    private int victimsRescued = 0;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        this.config = (args != null && args.length > 0 && args[0] instanceof SimulationConfig)
                ? (SimulationConfig) args[0] : SimulationConfig.defaults();
        registerWithDF();
        log.info("Base de secours prête. Capacité: {} drones", config.getDroneCount());
        addBehaviour(new HandleReturningDrones());
    }

    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_NAME);
        sd.setName(SERVICE_NAME);
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) {
            log.error("Base: Échec DF", e);
        }
    }

    private class HandleReturningDrones extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchOntology(ONTOLOGY_DRONE_RETURNED));

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive(mt);
            if (msg == null) { block(); return; }

            totalReturns++;
            log.debug("{} de retour à la base (retour #{})", msg.getSender().getLocalName(), totalReturns);

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setOntology(ONTOLOGY_NEW_MISSION);
            reply.setContent("new_mission");
            myAgent.send(reply);
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException ignored) {}
        log.info("Base terminée. Victimes secourues: {}/{}", victimsRescued, config.getVictimCount());
    }
}
