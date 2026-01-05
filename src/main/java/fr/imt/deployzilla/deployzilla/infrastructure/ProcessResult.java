package fr.imt.deployzilla.deployzilla.infrastructure;

public record ProcessResult(int exitCode, String output) {

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
