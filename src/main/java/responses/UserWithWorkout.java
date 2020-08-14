package responses;

import helpers.RequestFields;
import interfaces.Model;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import models.User;
import models.Workout;

@Data
public class UserWithWorkout implements Model {

    private User user;
    private Workout workout;

    public UserWithWorkout(User user, Workout workout) {
        this.user = user;
        this.workout = workout;
    }

    @Override
    public Map<String, Object> asMap() {
        Map<String, Object> retVal = new HashMap<>();
        retVal.putIfAbsent(RequestFields.USER, user.asMap());
        if (workout != null) {
            retVal.putIfAbsent(RequestFields.WORKOUT, workout.asMap());
        } else {
            // in case the user has no workout, just return an empty map
            retVal.putIfAbsent(RequestFields.WORKOUT, new HashMap<>());
        }
        return retVal;
    }
}