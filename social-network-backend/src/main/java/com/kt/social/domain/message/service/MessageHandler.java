//package com.kt.social.domain.message.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.socket.*;
//
//@Slf4j
//public class MessageHandler implements WebSocketHandler {
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        log.info("Connection Established on session: {}", session.getId());
//    }
//
//    @Override
//    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//        String payload = message.getPayload().toString();
//        log.info("Received message: {} from session: {}", payload, session.getId());
//        session.sendMessage(new TextMessage("Started processing your message: " + session + " - " + payload));
//        Thread.sleep(1000);
//        session.sendMessage(new TextMessage("Finished processing your message: " + payload));
//    }
//
//    @Override
//    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
//        log.info("Exception occured: {} on session: {}", exception.getMessage(), session.getId());
//    }
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//        log.info("Connection closed on session: {} with status: {}", session.getId(), closeStatus.getCode());
//    }
//
//    @Override
//    public boolean supportsPartialMessages() {
//        return false;
//    }
//}
