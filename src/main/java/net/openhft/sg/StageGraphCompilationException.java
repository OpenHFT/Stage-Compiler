package net.openhft.sg;

public class StageGraphCompilationException extends RuntimeException {
    
    
    public static StageGraphCompilationException sgce(String message) {
        return new StageGraphCompilationException(message);
    }
    
    public StageGraphCompilationException(String message) {
        super(message);
    }

    public StageGraphCompilationException(String message, Throwable cause) {
        super(message, cause);
    }

    public StageGraphCompilationException(Throwable cause) {
        super(cause);
    }
}
