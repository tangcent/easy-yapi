package com.itangcent.model;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Negative;
import javax.validation.constraints.NegativeOrZero;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public class ValidationDemoDto {

    @NotNull
    @Size(min = 10)
    private String minStr;

    @NotNull
    @Size(max = 100)
    private String maxStr;

    @NotNull
    @Size(min = 10, max = 100)
    private String rangeStr;

    @Pattern(regexp = "\\d{0,5}[a-z]{1,3}")
    private String regexStr;

    @Min(666)
    private Integer minInt;

    @Max(999)
    private Integer maxInt;

    @Min(666)
    private Double minDouble;

    @Max(999)
    private double maxDouble;

    @Min(666)
    @Max(999)
    private Integer rangeInt;

    @Min(66)
    @Max(9999)
    private float rangeFloat;

    @DecimalMin(value = "666", inclusive = false)
    @DecimalMax("9999")
    private Integer rangeLong;

    @DecimalMin("666")
    @DecimalMax(value = "9999", inclusive = false)
    private float rangeDouble;

    @Digits(integer = 6, fraction = 3)
    private Long digitLong;

    @Digits(integer = 6, fraction = 3)
    private Double digitDouble;

    @Negative
    private Integer negative;

    @NegativeOrZero
    private Integer negativeOrZero;

    @Positive
    private Integer positive;

    @PositiveOrZero
    private Integer positiveOrZero;

    @Positive
    private Float positiveFloat;

    @PositiveOrZero
    private float positiveOrZeroFloat;

    @Email
    private String email;

    @AssertTrue
    private boolean assertTrue;

    @AssertFalse
    private boolean assertFalse;

    @NotNull
    @Size(min = 1)
    private String[] minArr;

    @NotNull
    @Size(max = 5)
    private String[] maxArr;

    @NotNull
    @Size(min = 1, max = 5)
    private String[] rangeArr;

}
