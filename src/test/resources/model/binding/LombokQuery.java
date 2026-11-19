package com.itangcent.model.binding;

import lombok.Data;

/**
 * Lombok query object: the setters exist only after annotation processing.
 */
@Data
public class LombokQuery {

    private String name;

    private Integer size;
}
