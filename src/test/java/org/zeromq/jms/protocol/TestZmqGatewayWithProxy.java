package org.zeromq.jms.protocol;
/*
 * Copyright (c) 2015 Jeremy Miller
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import javax.jms.JMSException;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.jms.ZmqException;
import org.zeromq.jms.ZmqTextMessage;
import org.zeromq.jms.ZmqTextMessageBuilder;
import org.zeromq.jms.protocol.ZmqGateway.Direction;
import org.zeromq.jms.protocol.event.ZmqEventHandler;
import org.zeromq.jms.protocol.event.ZmqStompEventHandler;

/**
 * New test class to test the ZMQ Proxy functionality to enable n-1-n scenarios.
 */
public class TestZmqGatewayWithProxy {

    private static final String SOCKET_PROXY_ADDR = "tcp://*:9732";
    private static final String SOCKET_SERVER_ADDR = "tcp://*:9733";

    private static final String MESSAGE_1 = "this is the text message 1";
    private static final String MESSAGE_2 = "this is the text message 2";
    private static final String MESSAGE_3 = "this is the text message 3";

    /**
     * Test a n-1-n scenario were both "n"s are connecting and the proxy is bound.
     */
    @Test
    public void testPullPushWithProxy() {
        final int flags = 0;
        final ZmqEventHandler handler = new ZmqStompEventHandler();

        final ZmqSocketContext senderContext = new ZmqSocketContext(SOCKET_PROXY_ADDR, ZmqSocketType.PUSH, false, flags);
        final ZmqGateway sender1 = new ZmqFireAndForgetGateway("snd1", senderContext,
                null, handler, null, null, null, null, false, Direction.OUTGOING);
        final ZmqGateway sender2 = new ZmqFireAndForgetGateway("snd2", senderContext,
                null, handler, null, null, null, null, false, Direction.OUTGOING);

        final ZmqSocketContext receiverContext = new ZmqSocketContext(SOCKET_SERVER_ADDR, ZmqSocketType.PULL, false, flags);
        receiverContext.setProxyAddr(SOCKET_PROXY_ADDR);
        receiverContext.setProxyType(ZmqSocketType.PULL);
        receiverContext.setProxyOutType(ZmqSocketType.PUSH);

        final ZmqGateway receiver1 = new ZmqFireAndForgetGateway("rcv1", receiverContext,
                 null, handler, null, null, null, null, false, Direction.INCOMING);
        final ZmqGateway receiver2 = new ZmqFireAndForgetGateway("rcv2", receiverContext,
                null, handler, null, null, null, null, false, Direction.INCOMING);

        try {
            final ZmqTextMessage outMessage1 = ZmqTextMessageBuilder.create().appendText(MESSAGE_1).toMessage();
            final ZmqTextMessage outMessage2 = ZmqTextMessageBuilder.create().appendText(MESSAGE_2).toMessage();
            final ZmqTextMessage outMessage3 = ZmqTextMessageBuilder.create().appendText(MESSAGE_3).toMessage();

            receiver1.open(-1);
            receiver2.open(-1);

            sender1.open(-1);
            sender2.open(-1);

            try {
                sender1.send(outMessage1);

                ZmqTextMessage inMessage1 = (ZmqTextMessage) receiver1.receive(3000);

                if (inMessage1 == null) {
                    inMessage1 = (ZmqTextMessage) receiver2.receive(1000);
                }

                Assert.assertNotNull(inMessage1);
                Assert.assertEquals(MESSAGE_1, inMessage1.getText());

                sender1.send(outMessage2);

                ZmqTextMessage inMessage2 = (ZmqTextMessage) receiver1.receive(1000);

                if (inMessage2 == null) {
                    inMessage2 = (ZmqTextMessage) receiver2.receive(1000);
                }

                Assert.assertNotNull(inMessage2);
                Assert.assertEquals(MESSAGE_2, inMessage2.getText());

                sender2.send(outMessage3);

                ZmqTextMessage inMessage3 = (ZmqTextMessage) receiver1.receive(5000);

                if (inMessage3 == null) {
                    inMessage3 = (ZmqTextMessage) receiver2.receive(1000);
                }

                Assert.assertNotNull(inMessage3);
                Assert.assertEquals(MESSAGE_3, inMessage3.getText());
            } catch (ZmqException ex) {
                ex.printStackTrace();

                Assert.fail(ex.getMessage());
            } finally {
                sender1.close(-1);
                sender2.close(-1);

                receiver1.close(-1);
                receiver2.close(-1);
            }
        } catch (JMSException ex) {
            ex.printStackTrace();

            Assert.fail(ex.getMessage());
        }
    }
}
