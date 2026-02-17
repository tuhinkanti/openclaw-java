package ai.openclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;

public class RpcProtocol {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RpcMessage {
        private String id;
        private String method;
        private JsonNode params;
        private JsonNode result;
        private RpcError error;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public JsonNode getParams() {
            return params;
        }

        public void setParams(JsonNode params) {
            this.params = params;
        }

        public JsonNode getResult() {
            return result;
        }

        public void setResult(JsonNode result) {
            this.result = result;
        }

        public RpcError getError() {
            return error;
        }

        public void setError(RpcError error) {
            this.error = error;
        }
    }

    public static class RpcError {
        private int code;
        private String message;

        public RpcError() {
        }

        public RpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
