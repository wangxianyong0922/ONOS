package org.onosproject.NDN.NDNApp;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

public class ChangeName {

    private int max_components = 4;

    /**
     * 作用：验证json文件，提取json文件中的内容
     * @param :一个文件对象
     * */
    public Map<String,List<String>> operationFile(File file){

        //判断：文件的合法性
        if(!file.isFile()){
            System.out.println("错误：对象不是一个合法文件，请检查！");
            return null;
        }else if(!file.canRead()){
            System.out.println("错误：文件对象不可读，请检查！");
            return null;
        }else if(!file.isFile()){
            System.out.println("错误：文件不存在，请检查！");
            return null;
        }

        //用于接收文件中读出的内容
        ArrayList<String> arrayList = new ArrayList<String>();

        //读取文件中的内容（按行读）
        try{

            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bf = new BufferedReader(inputStreamReader);
            String str;
            while((str = bf.readLine()) != null){
                //每次读一行，读到str中
                arrayList.add(str);
            }
            bf.close();
            inputStreamReader.close();

        }catch(IOException e){

            System.out.println("错误：文件读写发生异常，请检查！");
            e.printStackTrace();

        }

        //对List中的内容进行处理
        Map<String,List<String>> result_set = new HashMap<String,List<String>>();
        int size = arrayList.size();
        System.out.println("提示：当前文件中传入的name个数为："+size);
        for(String temp:arrayList){
            //具体的对name进行处理
            List<String> result = processEntry(temp);
            //将name和result放到map中进行保存
            String[] rule = temp.split(" ");
            String name = rule[0];
            if(!result_set.containsKey(name)){
                result_set.put(name,result);
            }else{
                System.out.println("错误：当前name对应的rule信息已经保存在结果集中，请检查！");
            }
        }

        return result_set;

    }

    /**
     * 作用：接受调用者传递来的包含name信息的字符串，提取并分析
     * @param ：包含name信息的字符串
     * */
    public Map<String,List<String>> operationFile02(String nameInformation) {

        //验证信息
        String ni = nameInformation;
        if(ni == null || ni.equals("")){
            //说明字符串中没有内容
            return null;
        }

        //处理信息
        Map<String,List<String>> result_set = new HashMap<String,List<String>>();
        //切割，数组中的没一个元素均为“name+出接口”的信息形式
        String[] nis = ni.split(" ");
        for(int i = 0; i < nis.length; i++){
            //对每个name进行处理
            //注意，这里将字符串转换一下，将：替换为空格
            String replace = nis[i].replace(":", " ");
            List<String> result = processEntry(replace);
            //将name和result放到map中进行保存
            String[] rule = replace.split(" ");
            String name = rule[0];
            if(!result_set.containsKey(name)){
                result_set.put(name,result);
            }else{
                System.out.println("错误：当前name对应的rule信息已经保存在结果集中，请检查！");
            }
        }
        return result_set;
    }

    public List<String> processEntry(String entry){

        System.out.println("提示：当前处理的name为--->"+entry);
        String[] rule = entry.split(" ");
        String name = rule[0];
        int iface = Integer.parseInt(rule[1]);

        //获取name的组件信息
        String[] name_components = name.split("/");
        List<String> name_components_list = Arrays.stream(name_components).filter(p -> p != "").collect(Collectors.toList());
        //Collections.reverse(name_components_list);
        int temp_size = name_components_list.size();
        //System.out.println("调试：当前name_components_list的大小为--->"+temp_size);
        //System.out.println("调试：反转后的name_components_list中的内容为--->"+name_components_list.toString());
        /*name_components_list.remove(temp_size -1);
        Collections.reverse(name_components_list);
        System.out.println("提示：处理后的name_components_list中的内容为--->"+name_components_list.toString());*/
        //目前name前缀中组件的个数
        int prefix_ncomp = name_components_list.size();

        // 掩码的位置
        int str_position = 0;
        String binary_mask = "";
        String hash_name = null;
        if(rule.length == 3){
            if(rule[2].equals("*") || rule[2] == "*"){
                hash_name = computeHash(name_components_list);
                str_position = max_components - 1;
                binary_mask = "&&&0xffffffff";
            }
        }else{
            hash_name = computeHash(name_components_list);
            str_position = prefix_ncomp - 1;
            binary_mask = "&&&0xffff";
        }

        //needed：表示该由name生成的路由表项所能匹配的最大name个数
        int needed = max_components - prefix_ncomp + 1;
        int i = 0;
        String ternary_mask = "0&&&0 ";
        String masks_str = null;
        List<String> temp_ternary_mask = Collections.nCopies(max_components,ternary_mask);
        //System.out.println("temp_ternary_mask结果为："+temp_ternary_mask.toString());
        masks_str = String.join("",temp_ternary_mask);
        //System.out.println("masks_str结果为："+masks_str);
        String[] masks = masks_str.split(" ");
        masks[str_position] = String.format("0x%s%s",hash_name,binary_mask);
        List<String>  masks_list = Arrays.asList(masks);
        //System.out.println("masks_list结果为："+masks_list);
        //int temp_size01 = masks_list.size() - 1;
        /*System.out.println("masks_list.size() - 1结果为："+temp_size01);
        masks_list.remove(temp_size01);*/
        masks_str = String.join("-", masks_list);

        // result中保存针对一个name前缀生成的多条rule信息
        List<String> result = new ArrayList<String>();
        while(i < needed){
            // 上面已经完成name组件的CRC计算，将结果保存
            //String rule_info = String.format("table_add %s %s %d %s => %d %d", "fib_table", "set_egr", prefix_ncomp + i, masks_str, iface + 1, needed);
            // 当前使用较短的rule信息做测试
            String rule_info = String.format("index:%d compoents:%d iface:%d needed:%d mask:%s",prefix_ncomp + i,temp_size,iface + 1,needed,masks_str);
            System.out.println("提示：目前产生的rule信息为--->"+rule_info);
            result.add(rule_info);
            i += 1;
        }

        return result;
    }

    public String computeHash(List<String> name_components) {

        String result = null;
        //对name组件进行hash16的计算
        String str = "";
        for(String temp:name_components){
            str += temp;
        }
        //System.out.println("提示：即将进行crc计算的字符串为--->"+str);
        /*//首先将字符串转16进制
        String str16 = Hex.encodeHexStr(str.getBytes());
        System.out.println("提示：字符串"+str+"经过Hex转换后的结果为--->"+str16);*/
        //转为16进制bytes
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        //进行CRC计算（这里使用了三种计算方式，可以任选一种）
        String crc = CRCMod.getCRC(bytes);
        String crc2 = CRCMod.getCRC2(bytes);
        String crc3 = CRCMod.getCRC3(bytes);
        result = crc2;

        //------
        //尝试使用JDK的CRC32
        CRC32 crc32 = new CRC32();
        crc32.update(str.getBytes());
        long value = crc32.getValue();
        String s = Long.toHexString(value);
        System.out.println("JDK的CRC32计算出的结果为："+s);
        //------

        return result;
    }

    public void testFunction(String path_info){

        //对功能进行测试
        String path = path_info;
        File file = new File(path);
        Map<String, List<String>> result_set = operationFile(file);

    }

    /**
     * 作用：程序入口
     * @param 包含name信息的字符串
     * */
    public Map<String, List<String>> FunctionIngress(String nameInformation){
        Map<String, List<String>> stringListMap = operationFile02(nameInformation);
        //包含name和对应crc值的集合
        return stringListMap;
    }

    public static void main(String[] args) {
        ChangeName cn = new ChangeName();
        cn.testFunction("C:\\Users\\lenovo\\IdeaProjects\\wxy002\\test\\fib.txt");
    }
}
