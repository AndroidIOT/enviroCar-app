package org.envirocar.app.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.envirocar.app.logging.Logger;
import org.envirocar.app.storage.Measurement;
import org.envirocar.app.storage.Track;
import org.envirocar.app.storage.TrackAlreadyFinishedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import android.os.Environment;
import android.test.AndroidTestCase;
import android.util.Base64;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Environment.class, Logger.class, Base64.class})
public class TrackComparisonTest extends MockingEnvironmentTest {

	@Test
	public void testTracksWithMeasurements() throws TrackAlreadyFinishedException {
		Track t1 = Track.createLocalTrack();
		t1.setMeasurementsAsArrayList(Collections.singletonList(createMeasurement(0)));
		Track t2 = Track.createLocalTrack();
		t2.setMeasurementsAsArrayList(Collections.singletonList(createMeasurement(1)));
		Track t3 = Track.createLocalTrack();
		t3.setMeasurementsAsArrayList(Collections.singletonList(createMeasurement(2)));
		
		List<Track> list = createListAndSort(t1, t2, t3);
		
		Assert.assertTrue("Unexpected position!", list.get(0) == t3);
		Assert.assertTrue("Unexpected position!", list.get(1) == t2);
		Assert.assertTrue("Unexpected position!", list.get(2) == t1);
	}

    @Test
	public void testTracksWithoutMeasurements() {
		Track t1 = Track.createLocalTrack();
		Track t3 = Track.createLocalTrack();
		
		List<Track> list = createListAndSort(t1, t3);
		
		Assert.assertTrue("Unexpected position!", list.get(0) == t1);
		Assert.assertTrue("Unexpected position!", list.get(1) == t3);
	}

    @Test
	public void testOneTrackWithNoMeasurements() throws TrackAlreadyFinishedException {
		Track t1 = Track.createLocalTrack();
		t1.setMeasurementsAsArrayList(Collections.singletonList(createMeasurement(0)));
		Track t2 = Track.createLocalTrack();
		Track t3 = Track.createLocalTrack();
		
		List<Track> list = createListAndSort(t1, t2, t3);
		
		Assert.assertTrue("Unexpected position!", list.get(2) == t1);
	}
	
	private List<Track> createListAndSort(Track... t1) {
		List<Track> list = new ArrayList<Track>();
		
		for (Track track : t1) {
			list.add(track);
		}
		
		Collections.sort(list);
		return list;
	}
	
	private Measurement createMeasurement(int deltaMillis) {
		Measurement result = new Measurement(0.0, 0.0);
		result.setTime(System.currentTimeMillis()+deltaMillis);
		return result;
	}

}
