//package org.main.TSV;
//
//import tools.jackson.databind.ObjectMapper;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class ParametersParser {
//
//    Map<String, String> params = new HashMap<>();
//    String patternName;
//
//    public ParametersParser(String[] args) {
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            Map<String, Object> jsonMap = mapper.readValue(args[0], Map.class);
//            patternName = (String) jsonMap.get("pattern");
//            for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
//                if (!entry.getKey().equals("pattern")) {
//                    params.put(entry.getKey(), String.valueOf(entry.getValue()));
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("An error occurred.");
//            e.printStackTrace();
//        }
//    }
//}
