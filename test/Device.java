import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Device {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String id;
    private String name;
    private Map<String,String> data;

    public Device() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getData() {
        return data;
    }
    public void setData(Map<String, String> data) {
        this.data = data;
    }
    public Device(String id, String name, Map<String,String> data) {
        this.id = id;
        this.name = name;
        this.data = data;
    }

    public void print(){
        System.out.println("id: " + this.id);
        System.out.println("name: " + this.name);
        System.out.println("data: " + this.data);
    }
}
