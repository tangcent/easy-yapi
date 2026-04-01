package com.itangcent.model;

import java.util.List;
import java.util.Map;
import com.itangcent.model.Model;

/**
 * default model
 */
public class Default {

    /**
     * @default [123, 456]
     * @demo [666, 888]
     */
    private int[] intArr;

    /**
     * @default {"abc":"123","def":"456"}
     * @demo {"aaa":"666","ddd":"888"}
     */
    private Map<String, Long> amount;

    /**
     * @default ["abc","123"]
     * @demo ["aaa","666"]
     */
    private List<String> strings;

    /**
     * @default ["abc","123"}
     * @demo {"aaa","666"]
     */
    private List<String> invalid;

    /**
     * @default {"s":"aaa","s2":"bbb","stringList":"abc"}
     * @demo {"s":"aaa","s2":"bbb","stringList":"abc"}
     */
    private Model model;

    /**
     * @default [{"s":"aaa","s2":"bbb","stringList":"abc"}}
     * @demo [{"s":"aaa","s2":"bbb","stringList":"abc"}]
     */
    private List<Model> modelList;

    public int[] getIntArr() {
        return intArr;
    }

    public void setIntArr(int[] intArr) {
        this.intArr = intArr;
    }

    public Map<String, Long> getAmount() {
        return amount;
    }

    public void setAmount(Map<String, Long> amount) {
        this.amount = amount;
    }

    public List<String> getStrings() {
        return strings;
    }

    public void setStrings(List<String> strings) {
        this.strings = strings;
    }

    public List<String> getInvalid() {
        return invalid;
    }

    public void setInvalid(List<String> invalid) {
        this.invalid = invalid;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public List<Model> getModelList() {
        return modelList;
    }

    public void setModelList(List<Model> modelList) {
        this.modelList = modelList;
    }
}
