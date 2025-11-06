package com.tchvu3.capacitorvoicerecorder;

import com.getcapacitor.JSObject;
import org.json.JSONArray;

public class MessagesException extends Exception {

    public MessagesException(String message) {
        super(message);
    }

    public MessagesException(String message, Throwable cause) {
        super(message, cause);
    }

    public JSObject toJSObject() {
        JSObject toReturn = new JSObject();
        toReturn.put("message", this.getMessage());
        JSONArray causeMessagesArray = getCauseMessages();
        toReturn.put("causes", causeMessagesArray);
        return toReturn;
    }

    private JSONArray getCauseMessages() {
        JSONArray messages = new JSONArray();
        Throwable current = getCause();
        while (current != null) {
            messages.put(current.getMessage());
            current = current.getCause();
        }
        return messages;
    }
}
