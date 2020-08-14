package responses;

import com.amazonaws.services.dynamodbv2.document.Item;
import exceptions.InvalidAttributeException;
import interfaces.Model;
import java.util.Map;
import models.User;

public class UserResponse extends User implements Model {

    public UserResponse(Item user) throws InvalidAttributeException {
        super(user);
    }

    @Override
    public Map<String, Object> asMap() {
        return super.asMap();
    }
}