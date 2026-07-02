package ar.edu.unnoba.pdyc2026.common.exception;

/** Recurso en conflicto con uno existente (unicidad). Se mapea a HTTP 409 Conflict. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
