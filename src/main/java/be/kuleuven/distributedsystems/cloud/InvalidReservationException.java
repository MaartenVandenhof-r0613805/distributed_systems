package be.kuleuven.distributedsystems.cloud;

public class InvalidReservationException extends Exception{
    public InvalidReservationException (String errorMessage) {
        super(errorMessage);
    }
}
