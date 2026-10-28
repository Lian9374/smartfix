package com.smartfix.communitybridge;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.smartfix.notification.service.DeliveryWorker;
import static org.mockito.Mockito.*;
class Stage8InAppDeliveryTest {
 @Test void onlyInAppTasksAreSelectedAndOneFailureDoesNotLoseLaterTasks(){
  var db=mock(JdbcTemplate.class);var worker=mock(DeliveryWorker.class);
  when(db.queryForList(contains("channel='IN_APP'"),eq(Long.class))).thenReturn(java.util.List.of(1L,2L));
  doThrow(new IllegalStateException()).when(worker).deliver(1L);
  new Stage8InAppDelivery(db,worker).drain();verify(worker).deliver(2L);
 }
}
