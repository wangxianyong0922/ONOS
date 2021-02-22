package org.onosproject.NDN.NDNApp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

import static org.onosproject.net.config.Config.FieldPresence.OPTIONAL;

public class NDNNameConfig extends Config<ApplicationId> {

    //---Test:解析自定义json文件中的name字段
    private static final String NAME = "name";
    private static final String ONE = "one";
    private static final String TWO = "two";
    private static final String THREE = "three";
    //---


    @Override
    public boolean isValid() {
        return hasOnlyFields(NAME,ONE,TWO,THREE) && isString(ONE,OPTIONAL) && isString(TWO,OPTIONAL) && isString(THREE,OPTIONAL);
    }

    public String name(){
        String s = get(NAME, "111");
        return s;
    }

    public String one(){
        String s = get(ONE,"one默认值");
        return s;
    }

    public String two(){
        String s = get(TWO,"two默认值");
        return s;
    }

    public String three(){
        String s = get(THREE,"three默认值");
        return s;
    }

}
