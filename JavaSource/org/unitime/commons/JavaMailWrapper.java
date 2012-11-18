/*
 * UniTime 3.4 (University Timetabling Application)
 * Copyright (C) 2012, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
*/
package org.unitime.commons;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.unitime.timetable.ApplicationProperties;

public class JavaMailWrapper extends Email {
	private static Log sLog = LogFactory.getLog(Email.class);
	private javax.mail.Session iMailSession = null;
	private MimeMessage iMail = null;
	private Multipart iBody = null;
	
	public JavaMailWrapper() {
        Properties p = ApplicationProperties.getProperties();
        if (p.getProperty("mail.smtp.host")==null && p.getProperty("tmtbl.smtp.host")!=null)
            p.setProperty("mail.smtp.host", p.getProperty("tmtbl.smtp.host"));
        
        final String user = ApplicationProperties.getProperty("mail.smtp.user", ApplicationProperties.getProperty("unitime.email.user", ApplicationProperties.getProperty("tmtbl.mail.user")));
        final String password = ApplicationProperties.getProperty("mail.smtp.password", ApplicationProperties.getProperty("unitime.email.password", ApplicationProperties.getProperty("tmtbl.mail.pwd")));
        
        Authenticator a = null;
        if (user != null && password != null) {
            p.setProperty("mail.smtp.user", user);
            p.setProperty("mail.smtp.auth", "true");
            a = new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password); }
            };
        }

        iMailSession = javax.mail.Session.getDefaultInstance(p, a);
        iMail = new MimeMessage(iMailSession);
        iBody = new MimeMultipart();
	}
	
	@Override
	public void setSubject(String subject) throws MessagingException {
		iMail.setSubject(subject, "UTF-8");
	}
	
	@Override
	public void setFrom(String email, String name) throws MessagingException, UnsupportedEncodingException {
		if (email != null)
			iMail.setFrom(new InternetAddress(email, name, "UTF-8"));
	}

	@Override
	public void setReplyTo(String email, String name) throws UnsupportedEncodingException, MessagingException {
		if (email != null)
			iMail.setReplyTo(new InternetAddress[] {new InternetAddress(email, name, "UTF-8")});
	}
	
	protected void addRecipient(RecipientType type, String email, String name) throws UnsupportedEncodingException, MessagingException {
		iMail.addRecipient(type, new InternetAddress(email, name, "UTF-8"));
	}
	
	@Override
	public void addRecipient(String email, String name) throws UnsupportedEncodingException, MessagingException {
		addRecipient(RecipientType.TO, email, name);
	}
	
	@Override
	public void addRecipientCC(String email, String name) throws UnsupportedEncodingException, MessagingException {
		addRecipient(RecipientType.CC, email, name);
	}

	@Override
	public void addRecipientBCC(String email, String name) throws UnsupportedEncodingException, MessagingException {
		addRecipient(RecipientType.BCC, email, name);
	}
		
	@Override
	public void setBody(String message, String type) throws MessagingException {
        MimeBodyPart text = new MimeBodyPart(); text.setContent(message, type);
       iBody.addBodyPart(text);
	}
	
	@Override
	protected void addAttachement(String name, DataHandler data) throws MessagingException {
        BodyPart attachement = new MimeBodyPart();
        attachement.setDataHandler(data);
        attachement.setFileName(name);
        attachement.setHeader("Content-ID", name);
        iBody.addBodyPart(attachement);
	}
	
	@Override
	public void send() throws MessagingException, UnsupportedEncodingException {
		long t0 = System.currentTimeMillis();
		try {
			if (iMail.getFrom() == null || iMail.getFrom().length == 0)
		        setFrom(
		        		ApplicationProperties.getProperty("unitime.email.sender", 
		        				ApplicationProperties.getProperty("tmtbl.inquiry.sender", ApplicationProperties.getProperty("tmtbl.contact.email"))),
		        		ApplicationProperties.getProperty("unitime.email.sender.name", 
		        				ApplicationProperties.getProperty("tmtbl.inquiry.sender.name", ApplicationProperties.getProperty("tmtbl.contact.email.name", "UniTime Email")))
		        		);
	        if (iMail.getReplyTo() == null || iMail.getReplyTo().length == 0)
	        	setReplyTo(ApplicationProperties.getProperty("unitime.email.replyto"), ApplicationProperties.getProperty("unitime.email.replyto.name"));
	        iMail.setSentDate(new Date());
	        iMail.setContent(iBody);
	        iMail.saveChanges();
	        Transport.send(iMail);
		} finally {
			long t = System.currentTimeMillis() - t0;
			if (t > 30000)
				sLog.warn("It took " + new DecimalFormat("0.00").format(t / 1000.0) + " seconds to send an email.");
			else if (t > 5000)
				sLog.info("It took " + new DecimalFormat("0.00").format(t / 1000.0) + " seconds to send an email.");
		}
	}
	
	@Override
	public void setInReplyTo(String messageId) throws MessagingException {
		if (messageId != null)
			iMail.setHeader("In-Reply-To", messageId);
	}
	
	@Override
	public String getMessageId() throws MessagingException {
		return iMail.getHeader("Message-Id", null);
	}
}