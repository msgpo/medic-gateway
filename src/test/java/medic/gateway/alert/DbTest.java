package medic.gateway.alert;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.telephony.*;

import java.lang.reflect.*;
import java.util.*;

import medic.gateway.alert.test.*;

import org.junit.*;
import org.junit.runner.*;
import org.robolectric.*;
import org.robolectric.annotation.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static medic.gateway.alert.test.DbTestHelper.*;
import static medic.gateway.alert.test.TestUtils.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants=BuildConfig.class)
@SuppressWarnings({"PMD.JUnitTestsShouldIncludeAssert", "PMD.TooManyMethods"})
public class DbTest {
	private Db db;

	private DbTestHelper dbHelper;

	@Before
	public void setUp() throws Exception {
		dbHelper = new DbTestHelper(RuntimeEnvironment.application);
		db = dbHelper.db;

		db.setLogEntryLimit(50);
	}

	@After
	public void tearDown() throws Exception {
		dbHelper.tearDown();
	}

//> WtMessage TESTS
	@Test
	public void classParamsShouldBeInitialised() {
		assertNotNull(db);
		assertNotNull(dbHelper);
	}

	@Test
	public void canStoreWtMessages() {
		// given
		dbHelper.assertEmpty("wt_message");
		WtMessage m = aMessageWith(WtMessage.Status.WAITING);

		// when
		boolean successReported = db.store(m);

		// then
		assertTrue(successReported);
		dbHelper.assertCount("wt_message", 1);
	}

	@Test
	public void updateStatusFrom_shouldFailSilentlyIfMessageNotInDb() {
		// given
		WtMessage unsavedMessage = aMessageWith(WtMessage.Status.FORWARDED);

		// when
		db.updateStatusFrom(WtMessage.Status.WAITING, unsavedMessage);

		// then
		dbHelper.assertEmpty("wt_message");
	}

	@Test
	public void updateStatusFrom_shouldFailSilentlyIfWrongStatusFound() {
		// given
		String id = randomUuid();
		dbHelper.insert("wt_message",
				cols("_id", "status", "last_action", "_from", "content"),
				vals(id, WtMessage.Status.FORWARDED, 0, A_PHONE_NUMBER, SOME_CONTENT));
		WtMessage messageWithUpdatedStatus = aMessageWith(id, WtMessage.Status.FAILED);

		// when
		db.updateStatusFrom(WtMessage.Status.WAITING, messageWithUpdatedStatus);

		// then
		Cursor c = dbHelper.selectById("wt_message", cols("status", "last_action"), id);
		assertEquals("FORWARDED", c.getString(0));
		assertEquals(0, c.getLong(1));
	}

	@Test
	public void updateStatusFrom_shouldUpdateStatusOfMatchedMessageIfExpectedStatusFound() {
		// given
		String id = randomUuid();
		dbHelper.insert("wt_message",
				cols("_id", "status", "last_action", "_from", "content"),
				vals(id, WtMessage.Status.WAITING, 0, A_PHONE_NUMBER, SOME_CONTENT));
		WtMessage messageWithUpdatedStatus = aMessageWith(id, WtMessage.Status.FORWARDED);

		// when
		db.updateStatusFrom(WtMessage.Status.WAITING, messageWithUpdatedStatus);

		// then
		Cursor c = dbHelper.selectById("wt_message", cols("status", "last_action"), id);
		assertEquals("FORWARDED", c.getString(0));
		assertNotEquals(0, c.getLong(1));
	}

//> SmsMessage TESTS
	@Test
	public void canStoreSmsMessages() {
		// given
		dbHelper.assertEmpty("wt_message");
		SmsMessage m = anSmsWith(A_PHONE_NUMBER, SOME_CONTENT);

		// when
		boolean successReported = db.store(m);

		// then
		assertTrue(successReported);
		dbHelper.assertTable("wt_message",
				ANY_ID, "WAITING", ANY_NUMBER, A_PHONE_NUMBER, SOME_CONTENT);
	}

//> WoMessage TESTS
	@Test
	public void canStoreWoMessages() {
		// given
		dbHelper.assertEmpty("wo_message");
		WoMessage m = aMessageWith(WoMessage.Status.PENDING);

		// when
		boolean successReported = db.store(m);

		// then
		assertTrue(successReported);
		dbHelper.assertCount("wo_message", 1);
	}

	@Test
	public void store_WoMessage_duplicate_shouldMarkAsNeedsForwarding() {
		// given: there is a message in the database which does NOT need forwarding
		String id = randomUuid();
		dbHelper.insert("wo_message",
				cols("_id", "status", "status_needs_forwarding", "failure_reason", "last_action", "_to", "content"),
				vals(id, WoMessage.Status.PENDING, false, null, 0, A_PHONE_NUMBER, SOME_CONTENT));

		// when: try to store the same message again
		db.store(new WoMessage(id, A_PHONE_NUMBER, SOME_CONTENT));

		// then: none of the message details have changed except last action and needs forwarding
		dbHelper.assertTable("wo_message",
				id, "PENDING", true, null, GT_ZERO, A_PHONE_NUMBER, SOME_CONTENT);
	}

	@Test
	public void updateStatus_shouldFailSilentlyIfMessageNotInDb() {
		// given
		WoMessage unsavedMessage = aMessageWith(WoMessage.Status.PENDING);

		// when
		db.updateStatus(unsavedMessage, WoMessage.Status.PENDING, WoMessage.Status.DELIVERED);

		// then
		dbHelper.assertEmpty("wt_message");
	}

	@Test
	public void updateStatus_shouldFailSilentlyIfWrongStatusFound() {
		// given
		String id = randomUuid();
		dbHelper.insert("wo_message",
				cols("_id", "status", "status_needs_forwarding", "failure_reason", "last_action", "_to", "content"),
				vals(id, WoMessage.Status.FAILED, false, "failure-reason", 0, A_PHONE_NUMBER, SOME_CONTENT));
		WoMessage messageWithUpdatedStatus = aMessageWith(id, WoMessage.Status.PENDING);

		// when
		db.updateStatus(messageWithUpdatedStatus, WoMessage.Status.PENDING, WoMessage.Status.DELIVERED);

		// then
		Cursor c = dbHelper.selectById("wo_message", cols("status", "last_action"), id);
		assertEquals("FAILED", c.getString(0));
		assertEquals(0, c.getLong(1));
	}

	@Test
	public void updateStatus_shouldUpdateStatusOfMatchedMessageIfExpectedStatusFound() {
		// given
		String id = randomUuid();
		dbHelper.insert("wo_message",
				cols("_id", "status", "status_needs_forwarding", "last_action", "_to", "content"),
				vals(id, WoMessage.Status.PENDING, false, 0, A_PHONE_NUMBER, SOME_CONTENT));
		WoMessage messageWithUpdatedStatus = aMessageWith(id, WoMessage.Status.PENDING);

		// when
		db.updateStatus(messageWithUpdatedStatus, WoMessage.Status.PENDING, WoMessage.Status.DELIVERED);

		// then
		Cursor c = dbHelper.selectById("wo_message", cols("status", "last_action"), id);
		assertEquals("DELIVERED", c.getString(0));
		assertNotEquals(0, c.getLong(1));
	}

//> WoMessage.StatusUpdate TESTS
	@Test
	public void woMessage_store_shouldCreateNewStatusUpdateTableEntry() {
		// given
		WoMessage m = aMessageWith(WoMessage.Status.UNSENT);
		dbHelper.assertCount("wo_message", 0);
		dbHelper.assertCount("wom_status", 0);

		// when
		db.store(m);

		// then
		dbHelper.assertTable("wom_status",
				ANY_NUMBER, m.id, "UNSENT", null, ANY_NUMBER, true);
	}

	@Test
	public void updateStatus_shouldCreateNewStatusUpdateTableEntry() {
		// given
		WoMessage m = aMessageWith(WoMessage.Status.UNSENT);
		db.store(m);
		dbHelper.assertCount("wo_message", 1);
		dbHelper.assertCount("wom_status", 1);

		// when
		db.updateStatus(m, WoMessage.Status.PENDING);

		// then
		dbHelper.assertTable("wom_status",
				ANY_NUMBER, m.id, "UNSENT", null, ANY_NUMBER,
				ANY_NUMBER, m.id, "PENDING", null, ANY_NUMBER);
	}

	@Test
	public void statusUpdates_shouldBeReturnedInDbOrderForTheRelevantMessage() {
		// given
		WoMessage m = aMessageWith("relevant", WoMessage.Status.SENT);
		dbHelper.insert("wom_status",
				cols("_id", "message_id", "status",  "timestamp"),
				vals(1,     "random1",    "PENDING", 111),
				vals(2,     "relevant",   "PENDING", 222),
				vals(3,     "random2",    "UNSENT",  333),
				vals(4,     "relevant",   "SENT",    444),
				vals(5,     "random3",    "FAILED",  555));

		// when
		List<WoMessage.StatusUpdate> updates = db.getStatusUpdates(m);

		// then
		assertEquals(updates,
				new WoMessage.StatusUpdate("relevant", WoMessage.Status.PENDING, null, 223),
				new WoMessage.StatusUpdate("relevant", WoMessage.Status.SENT, null, 445));
	}

//> deleteOldData() TESTS
	@Test
	public void deleteOldData_shouldHaveNoEffectIfNoData() {
		// given
		dbHelper.assertEmpty("log");
		dbHelper.assertEmpty("wo_message");
		dbHelper.assertEmpty("wt_message");

		// when
		int deletedCount = db.deleteOldData();

		// then
		assertEquals(0, deletedCount);
		dbHelper.assertEmpty("log");
		dbHelper.assertEmpty("wo_message");
		dbHelper.assertEmpty("wt_message");
	}

	@Test
	public void deleteOldData_shouldDeleteOldEventLogsButNotNewOnes() {
		// given
		dbHelper.insert("log",
				cols("_id", "timestamp", "message"),
				vals(1, now(), "should not delete 1"),
				vals(2, daysAgo(8), "should delete 1"),
				vals(3, daysAgo(6), "should not delete 2"),
				vals(4, daysAgo(800), "should delete 2"));
		dbHelper.assertCount("log", 4);

		// when
		int deletedCount = db.deleteOldData();

		// then
		assertEquals(2, deletedCount);
		dbHelper.assertTable("log",
				ANY_ID, ANY_NUMBER, "should not delete 1",
				ANY_ID, ANY_NUMBER, "should not delete 2");
	}

	@Test
	public void deleteOldData_shouldDeleteOldWoMessagesButNotNewOnes() {
		// given
		dbHelper.insert("wo_message",
				cols("_id", "status", "status_needs_forwarding", "last_action", "_to", "content"),
				vals(randomUuid(), WoMessage.Status.PENDING, false, now(), A_PHONE_NUMBER, "should keep 1"),
				vals(randomUuid(), WoMessage.Status.PENDING, false, daysAgo(8), A_PHONE_NUMBER, "should delete 1"),
				vals(randomUuid(), WoMessage.Status.PENDING, false, daysAgo(6), A_PHONE_NUMBER, "should keep 2"),
				vals(randomUuid(), WoMessage.Status.PENDING, false, daysAgo(800), A_PHONE_NUMBER, "should delete 2"));
		dbHelper.assertCount("wo_message", 4);

		// when
		int deletedCount = db.deleteOldData();

		// then
		assertEquals(2, deletedCount);
		dbHelper.assertTable("wo_message",
				ANY_ID, "PENDING", false, null, ANY_NUMBER, A_PHONE_NUMBER, "should keep 1",
				ANY_ID, "PENDING", false, null, ANY_NUMBER, A_PHONE_NUMBER, "should keep 2");
	}

	@Test
	public void deleteOldData_shouldDeleteOldWtMessagesButNotNewOnes() {
		// given
		dbHelper.insert("wt_message",
				cols("_id", "status", "last_action", "_from", "content"),
				vals(randomUuid(), WoMessage.Status.PENDING, now(), A_PHONE_NUMBER, "should keep 1"),
				vals(randomUuid(), WoMessage.Status.PENDING, daysAgo(8), A_PHONE_NUMBER, "should delete 1"),
				vals(randomUuid(), WoMessage.Status.PENDING, daysAgo(6), A_PHONE_NUMBER, "should keep 2"),
				vals(randomUuid(), WoMessage.Status.PENDING, daysAgo(800), A_PHONE_NUMBER, "should delete 2"));
		dbHelper.assertCount("wt_message", 4);

		// when
		int deletedCount = db.deleteOldData();

		// then
		assertEquals(2, deletedCount);
		dbHelper.assertTable("wt_message",
				ANY_ID, "PENDING", ANY_NUMBER, A_PHONE_NUMBER, "should keep 1",
				ANY_ID, "PENDING", ANY_NUMBER, A_PHONE_NUMBER, "should keep 2");
	}

	@Test
	public void deleteOldData_shouldDeleteAllKindsOfData() {
		// given
		dbHelper.insert("log",
				cols("_id", "timestamp", "message"),
				vals(1, daysAgo(8), "Should be deleted"));
		dbHelper.insert("wo_message",
				cols("_id", "status", "status_needs_forwarding", "last_action", "_to", "content"),
				vals(randomUuid(), WoMessage.Status.PENDING, false, daysAgo(8), A_PHONE_NUMBER, "should delete"));
		dbHelper.insert("wt_message",
				cols("_id", "status", "last_action", "_from", "content"),
				vals(randomUuid(), WoMessage.Status.PENDING, daysAgo(8), A_PHONE_NUMBER, "should delete 1"));
		dbHelper.assertCount("log", 1);
		dbHelper.assertCount("wo_message", 1);
		dbHelper.assertCount("wt_message", 1);

		// when
		int deletedCount = db.deleteOldData();

		// then
		assertEquals(3, deletedCount);
		dbHelper.assertEmpty("log");
		dbHelper.assertEmpty("wo_message");
		dbHelper.assertEmpty("wt_message");
	}

//> cleanLogs() TESTS
	@Test
	public void cleanLogs_shouldNotComplainIfNoLogs() {
		// given: nothing in the db

		// when:
		db.cleanLogs();

		// then
		dbHelper.assertEmpty("log");
	}

	@Test
	public void cleanLogs_shouldNotDeleteAnythingIfNotEnoughLogs() {
		// given:
		for(int i=0; i<50; ++i) dbHelper.insert("log", cols("timestamp", "message"), vals(now(), "entry: " + i));
		dbHelper.assertCount("log", 50);

		// when:
		db.cleanLogs();

		// then
		dbHelper.assertCount("log", 50);
	}

	@Test
	public void cleanLogs_shouldOnlyDeleteOldestEntriesOverTheLimit() {
		// given
		for(int i=0; i<100; ++i) dbHelper.insert("log", cols("timestamp", "message"), vals(now(), "entry: " + i));
		dbHelper.assertCount("log", 100);

		// when:
		db.cleanLogs();

		// then
		dbHelper.assertCount("log", 50);
		dbHelper.assertValues("log", cols("message"),
				"entry: 50", "entry: 51", "entry: 52", "entry: 53", "entry: 54",
				"entry: 55", "entry: 56", "entry: 57", "entry: 58", "entry: 59",
				"entry: 60", "entry: 61", "entry: 62", "entry: 63", "entry: 64",
				"entry: 65", "entry: 66", "entry: 67", "entry: 68", "entry: 69",
				"entry: 70", "entry: 71", "entry: 72", "entry: 73", "entry: 74",
				"entry: 75", "entry: 76", "entry: 77", "entry: 78", "entry: 79",
				"entry: 80", "entry: 81", "entry: 82", "entry: 83", "entry: 84",
				"entry: 85", "entry: 86", "entry: 87", "entry: 88", "entry: 89",
				"entry: 90", "entry: 91", "entry: 92", "entry: 93", "entry: 94",
				"entry: 95", "entry: 96", "entry: 97", "entry: 98", "entry: 99");
	}

	@Test
	public void cleanLogs_shouldOnlyDeleteOldestEntriesOverTheLimit_withMoreMessages() {
		// given
		for(int i=0; i<500; ++i) dbHelper.insert("log", cols("timestamp", "message"), vals(now(), "entry: " + i));
		dbHelper.assertCount("log", 500);

		// when:
		db.cleanLogs();

		// then
		dbHelper.assertCount("log", 50);
		dbHelper.assertValues("log", cols("message"),
				"entry: 450", "entry: 451", "entry: 452", "entry: 453", "entry: 454",
				"entry: 455", "entry: 456", "entry: 457", "entry: 458", "entry: 459",
				"entry: 460", "entry: 461", "entry: 462", "entry: 463", "entry: 464",
				"entry: 465", "entry: 466", "entry: 467", "entry: 468", "entry: 469",
				"entry: 470", "entry: 471", "entry: 472", "entry: 473", "entry: 474",
				"entry: 475", "entry: 476", "entry: 477", "entry: 478", "entry: 479",
				"entry: 480", "entry: 481", "entry: 482", "entry: 483", "entry: 484",
				"entry: 485", "entry: 486", "entry: 487", "entry: 488", "entry: 489",
				"entry: 490", "entry: 491", "entry: 492", "entry: 493", "entry: 494",
				"entry: 495", "entry: 496", "entry: 497", "entry: 498", "entry: 499");
	}

//> STATIC HELPERS
	private static WtMessage aMessageWith(WtMessage.Status status) {
		return aMessageWith(randomUuid(), status);
	}

	private static WtMessage aMessageWith(String id, WtMessage.Status status) {
		return new WtMessage(id, status, System.currentTimeMillis(), A_PHONE_NUMBER, SOME_CONTENT);
	}

	private static WoMessage aMessageWith(WoMessage.Status status) {
		return aMessageWith(randomUuid(), status);
	}

	private static WoMessage aMessageWith(String id, WoMessage.Status status) {
		return new WoMessage(id, status, null, System.currentTimeMillis(), A_PHONE_NUMBER, SOME_CONTENT);
	}

	private static SmsMessage anSmsWith(String from, String content) {
		SmsMessage m = mock(SmsMessage.class);
		when(m.getMessageBody()).thenReturn(content);
		when(m.getOriginatingAddress()).thenReturn(from);
		return m;
	}
}
