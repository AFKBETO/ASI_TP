package TP1.Exo2;

import java.net.URI;
import java.net.URISyntaxException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;

public class JmsTopicExample2 implements MessageListener {
    private Session serverSession;
    private MessageProducer replyProducer;

    public static void main(String[] args) throws URISyntaxException, Exception {
        JmsTopicExample2 jmsTopicExample = new JmsTopicExample2();
        jmsTopicExample.sendReqOnTempTopic();
    }

    public void sendReqOnTempTopic() throws URISyntaxException, Exception {
        BrokerService broker = BrokerFactory.createBroker(new URI(
                "broker:(tcp://localhost:61616)"));
        broker.start();
        Connection serverConnection = null;
        Connection clientConnection = null;
        try {
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                    "tcp://localhost:61616");

            serverConnection = connectionFactory.createConnection();
            serverConnection.setClientID("serverTempTopic");
            serverSession = serverConnection.createSession(false,
                    Session.AUTO_ACKNOWLEDGE);

            replyProducer = serverSession.createProducer(null);
            Topic requestDestination = serverSession.createTopic("SomeTopic");

            //Server is listening for queries
            final MessageConsumer requestConsumer = serverSession
                    .createConsumer(requestDestination);
            requestConsumer.setMessageListener(this);
            serverConnection.start();

            // Client sends a query to topic 'SomeTopic'
            clientConnection = connectionFactory.createConnection();
            clientConnection.setClientID("clientTempTopic");
            Session clientSession = clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            clientConnection.start();
            Destination replyDestination = clientSession.createTemporaryTopic();

            MessageProducer requestProducer = clientSession.createProducer(requestDestination);
            //Create a client listener on the temporary topic
            MessageConsumer replyConsumer = clientSession.createConsumer(replyDestination);

            TextMessage requestMessage = clientSession.createTextMessage("Client: Important Query");
            //Server is going to send the reply to the temporary topic
            requestMessage.setJMSReplyTo(replyDestination);
            requestProducer.send(requestMessage);

            System.out.println("Sent request " + requestMessage);

            //Read the answer from temporary queue.
            Message msg = replyConsumer.receive(5000);
            TextMessage replyMessage = (TextMessage)msg;
            System.out.println("Received reply " + replyMessage.toString());
            System.out.println("Received answer: " + replyMessage.getText());

            replyConsumer.close();

            clientSession.close();
            serverSession.close();
        } finally {
            if (clientConnection != null) {
                clientConnection.close();
            }
            if (serverConnection != null) {
                serverConnection.close();
            }
            broker.stop();
        }
    }

    //Server receives a query and sends reply to temporary topic set in JMSReplyTo
    public void onMessage(Message message) {
        try {
            TextMessage requestMessage = (TextMessage)message;

            System.out.println("Received request." + requestMessage.toString());

            Destination replyDestination = requestMessage.getJMSReplyTo();
            TextMessage replyMessage = serverSession.createTextMessage("Server: This is my answer to " + requestMessage.getText());

            replyMessage.setJMSCorrelationID(requestMessage.getJMSMessageID());

            replyProducer = serverSession.createProducer(replyDestination);
            replyProducer.send(replyMessage);

            System.out.println("Sent reply.");
            System.out.println(replyMessage);
        } catch (JMSException e) {
            System.out.println(e);
        }
    }
}