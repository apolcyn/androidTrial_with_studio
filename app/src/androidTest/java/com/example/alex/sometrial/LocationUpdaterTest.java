package com.example.alex.sometrial;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by Alex on 11/22/2015.
 */
public class LocationUpdaterTest extends TestCase {

    public void testInsidePolyCampus() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        LocationUpdater locationUpdater = new LocationUpdater();
        Method insidePolyCampus = LocationUpdater.class.getDeclaredMethod("insidePolyCampus", MinimalLocation.class);
        insidePolyCampus.setAccessible(true);

        Assert.assertEquals(true, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.301062, -120.660652)));
        Assert.assertEquals(true, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.298270, -120.663875)));
        Assert.assertEquals(true, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.299391, -120.657459)));
        Assert.assertEquals(true, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.297727, -120.661729)));

        Assert.assertEquals(false, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.293444, -120.650895)));
        Assert.assertEquals(false, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.300138, -120.668778)));
        Assert.assertEquals(false, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.308248, -120.665886)));
        Assert.assertEquals(false, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.305516, -120.654943)));
        Assert.assertEquals(false, insidePolyCampus.invoke(locationUpdater, MinimalLocation.newMinimalLocation(35.296480, -120.662367)));
    }
}
