package fr.imt.deployzilla.deployzilla.business.exception;

public class ProjectNotFoundException extends RuntimeException{

    public ProjectNotFoundException(String message) {
        super(message);
    }

}
