package org.apache.aries.tx.control.service.local.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.osgi.service.transaction.control.TransactionStatus.COMMITTED;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLED_BACK;

import java.net.BindException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.transaction.control.LocalResource;
import org.osgi.service.transaction.control.ResourceProvider;
import org.osgi.service.transaction.control.ScopedWorkException;
import org.osgi.service.transaction.control.TransactionStatus;

@RunWith(MockitoJUnitRunner.class)
public class TransactionControlRunningTest {

	@Mock
	ResourceProvider<Object> testProvider;
	@Mock
	LocalResource testResource;

	TransactionControlImpl txControl;

	@Before
	public void setUp() {
		txControl = new TransactionControlImpl();
	}

	@Test
	public void testRequired() {

		AtomicReference<TransactionStatus> finalStatus = new AtomicReference<>();
		
		txControl.required(() -> {

			assertTrue(txControl.activeTransaction());

			txControl.getCurrentContext().postCompletion(finalStatus::set);
			return null;
		});
		
		assertEquals(COMMITTED, finalStatus.get());

	}

	@Test
	public void testRequiredMarkedRollback() {
		
		AtomicReference<TransactionStatus> finalStatus = new AtomicReference<>();
		
		txControl.required(() -> {
			
			assertTrue(txControl.activeTransaction());
			
			txControl.getCurrentContext().postCompletion(finalStatus::set);
			
			txControl.setRollbackOnly();
			return null;
		});
		
		assertEquals(ROLLED_BACK, finalStatus.get());
	}

	@Test
	public void testRequiredUserException() {
		
		AtomicReference<TransactionStatus> finalStatus = new AtomicReference<>();
		
		Exception userEx = new Exception("Bang!");
		
		try {
			txControl.required(() -> {
				
				assertTrue(txControl.activeTransaction());
				
				txControl.getCurrentContext().postCompletion(finalStatus::set);
				
				throw userEx;
			});
			fail("Should not be reached");
		} catch (ScopedWorkException swe) {
			assertSame(userEx, swe.getCause());
		}
		
		assertEquals(ROLLED_BACK, finalStatus.get());
	}

	@Test
	public void testRequiredNoRollbackException() {
		
		AtomicReference<TransactionStatus> finalStatus = new AtomicReference<>();
		
		Exception userEx = new BindException("Bang!");
		
		try {
			txControl.build()
				.noRollbackFor(BindException.class)
				.required(() -> {
					
					assertTrue(txControl.activeTransaction());
					
					txControl.getCurrentContext().postCompletion(finalStatus::set);
					
					throw userEx;
				});
			fail("Should not be reached");
		} catch (ScopedWorkException swe) {
			assertSame(userEx, swe.getCause());
		}
		
		assertEquals(COMMITTED, finalStatus.get());
	}

	@Test
	public void testTwoRequiredsNested() {

		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		txControl.required(() -> {

			assertTrue(txControl.activeTransaction());
			
			Object key = txControl.getCurrentContext().getTransactionKey();

			txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
			
			txControl.requiresNew(() -> {
					assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
					
					txControl.getCurrentContext().postCompletion(finalStatusInner::set);
					return null;
				});
			
			return null;
		});
		
		assertEquals(COMMITTED, finalStatusOuter.get());
		assertEquals(COMMITTED, finalStatusInner.get());

	}

	@Test
	public void testTwoRequiredsNestedOuterMarkedRollback() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		txControl.required(() -> {
			
			assertTrue(txControl.activeTransaction());
			
			Object key = txControl.getCurrentContext().getTransactionKey();
			
			txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
			
			txControl.setRollbackOnly();
			
			txControl.requiresNew(() -> {
				assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
				
				txControl.getCurrentContext().postCompletion(finalStatusInner::set);
				return null;
			});
			
			return null;
		});
		
		assertEquals(ROLLED_BACK, finalStatusOuter.get());
		assertEquals(COMMITTED, finalStatusInner.get());
		
	}

	@Test
	public void testTwoRequiredsNestedInnerMarkedRollback() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		txControl.required(() -> {
			
			assertTrue(txControl.activeTransaction());
			
			Object key = txControl.getCurrentContext().getTransactionKey();
			
			txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
			
			txControl.requiresNew(() -> {
				assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
				
				txControl.getCurrentContext().postCompletion(finalStatusInner::set);

				txControl.setRollbackOnly();
				
				return null;
			});
			
			return null;
		});
		
		assertEquals(COMMITTED, finalStatusOuter.get());
		assertEquals(ROLLED_BACK, finalStatusInner.get());
		
	}

	@Test
	public void testTwoRequiredsNestedBothMarkedRollback() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		txControl.required(() -> {
			
			assertTrue(txControl.activeTransaction());
			
			Object key = txControl.getCurrentContext().getTransactionKey();
			
			txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
			
			txControl.setRollbackOnly();

			txControl.requiresNew(() -> {
				assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
				
				txControl.getCurrentContext().postCompletion(finalStatusInner::set);
				
				txControl.setRollbackOnly();
				
				return null;
			});
			
			return null;
		});
		
		assertEquals(ROLLED_BACK, finalStatusOuter.get());
		assertEquals(ROLLED_BACK, finalStatusInner.get());
		
	}
	
	@Test
	public void testTwoRequiredsNestedOuterThrowsException() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		Exception userEx = new Exception("Bang!");
		
		try {
			txControl.required(() -> {
				
				assertTrue(txControl.activeTransaction());
				
				Object key = txControl.getCurrentContext().getTransactionKey();
				
				txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
				
				txControl.setRollbackOnly();
				
				txControl.requiresNew(() -> {
					assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
					
					txControl.getCurrentContext().postCompletion(finalStatusInner::set);
					return null;
				});
				
				throw userEx;
			});
			fail("Should not be reached");
		} catch (ScopedWorkException swe) {
			assertSame(userEx, swe.getCause());
		}
		
		assertEquals(ROLLED_BACK, finalStatusOuter.get());
		assertEquals(COMMITTED, finalStatusInner.get());
		
	}
	
	@Test
	public void testTwoRequiredsNestedInnerThrowsException() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		Exception userEx = new Exception("Bang!");
		
		txControl.required(() -> {
			
			assertTrue(txControl.activeTransaction());
			
			Object key = txControl.getCurrentContext().getTransactionKey();
			
			txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
			
			try {
				txControl.requiresNew(() -> {
					assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
					
					txControl.getCurrentContext().postCompletion(finalStatusInner::set);
					
					txControl.setRollbackOnly();
					
					throw userEx;
				});
				fail("Should not be reached!");
			} catch (ScopedWorkException swe) {
				assertSame(userEx, swe.getCause());
			}
			return null;
		});
		
		assertEquals(COMMITTED, finalStatusOuter.get());
		assertEquals(ROLLED_BACK, finalStatusInner.get());
		
	}
	
	@Test
	public void testTwoRequiredsNestedNoRollbackForInnerException() {
		
		AtomicReference<TransactionStatus> finalStatusOuter = new AtomicReference<>();
		AtomicReference<TransactionStatus> finalStatusInner = new AtomicReference<>();
		
		Exception userEx = new BindException("Bang!");
		
		try {
			txControl.required(() -> {
				
				assertTrue(txControl.activeTransaction());
				
				Object key = txControl.getCurrentContext().getTransactionKey();
				
				txControl.getCurrentContext().postCompletion(finalStatusOuter::set);
				
				try {
					txControl.build()
						.noRollbackFor(BindException.class)
						.requiresNew(() -> {
								assertFalse(key.equals(txControl.getCurrentContext().getTransactionKey()));
								
								txControl.getCurrentContext().postCompletion(finalStatusInner::set);
								
								throw userEx;
							});
					fail("Should not be reached!");
				} catch (ScopedWorkException swe) {
					throw swe.as(BindException.class);
				}
				
				return null;
			});
			fail("Should not be reached!");
		} catch (ScopedWorkException swe) {
			assertSame(userEx, swe.getCause());
		}
		
		assertEquals(ROLLED_BACK, finalStatusOuter.get());
		assertEquals(COMMITTED, finalStatusInner.get());
		
	}
	
}
