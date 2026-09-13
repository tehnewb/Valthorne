package java.util.concurrent;
public class CompletionException extends RuntimeException {
 protected CompletionException(){}
 protected CompletionException(String message){super(message);}
 public CompletionException(Throwable cause){super(cause);}
 public CompletionException(String message,Throwable cause){super(message,cause);}
}
