/*
 * Copyright (c) 2016, Mihai Emil Andronache
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  1)Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *  2)Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *  3)Neither the name of charles-github-ejb nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.amihaiemil.charles.github;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.ServerSocket;

import javax.json.Json;
import javax.json.JsonObject;
import javax.mail.Message;
import javax.mail.internet.MimeMessage;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import com.amihaiemil.charles.steps.IndexSite;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.jcabi.github.Issue;
import com.jcabi.github.mock.MkGithub;

/**
 * Unit tests for {@link IndexSiteSteps}
 * @author Mihai Andronache (amihaiemil@gmail.com)
 * @version $Id$
 * @sice 1.0.0
 *
 */
public class IndexSiteStepsTestCase {

    /**
     * {@link IndexSiteSteps.IndexSiteStepsBuilder.build()} can build an IndexSiteSteps instance.
     */
    @Test
    public void builderWorks() {
        IndexSiteSteps iss = new IndexSiteSteps.IndexSiteStepsBuilder(
            Mockito.mock(Command.class),
            Mockito.mock(JsonObject.class),
            Mockito.mock(Language.class),
            Mockito.mock(Logger.class)
        )
    	.authorOwnerCheck(Mockito.mock(AuthorOwnerCheck.class))
    	.repoNameCheck(Mockito.mock(RepoNameCheck.class))
    	.ghPagesBranchCheck(Mockito.mock(GhPagesBranchCheck.class))
    	.build();
    	assertTrue(iss != null);
    }
    
    /**
     * The check for author identity fails and a denial reply is sent to the commander.
     * @throws Exception If something goes wrong.
     */
    @Test
    public void authorCheckNameFails() throws Exception {
    	AuthorOwnerCheck aoc = Mockito.mock(AuthorOwnerCheck.class);
    	Mockito.when(aoc.perform()).thenReturn(false);
    	
    	Command com = this.mockCommand("amihaiemil", "amihaiemil@gmail.com", "owner");
    	
    	Language lang = Mockito.mock(Language.class);
    	Mockito.when(lang.response("denied.commander.comment")).thenReturn("Expected repo owner!");
    	
    	IndexSiteSteps iss = new IndexSiteSteps.IndexSiteStepsBuilder(
            com,
            com.issue().repo().json(),
            lang,
            Mockito.mock(Logger.class)
        )
    	.authorOwnerCheck(aoc)
        .build();
    	assertTrue(iss.perform());
    	String deniedMessage = com.issue().comments().iterate().iterator().next().json().getString("body");
    	assertTrue(deniedMessage.contains("Expected repo owner!"));
    }
    
    /**
     * The repository doesn't match the name and it does not have a gh-pages branch.
     * @throws Exception If something goes worng.
     */
    @Test
    public void repoNameAndGhPagesCheckFails() throws Exception {
    	RepoForkCheck rfc = Mockito.mock(RepoForkCheck.class);
    	Mockito.when(rfc.perform()).thenReturn(true);

    	AuthorOwnerCheck aoc = Mockito.mock(AuthorOwnerCheck.class);
    	Mockito.when(aoc.perform()).thenReturn(true);

    	RepoNameCheck rnc = Mockito.mock(RepoNameCheck.class);
    	Mockito.when(rnc.perform()).thenReturn(false);

    	GhPagesBranchCheck ghc = Mockito.mock(GhPagesBranchCheck.class);
    	Mockito.when(ghc.perform()).thenReturn(false);

    	
    	Command com = this.mockCommand("amihaiemil", "amihaiemil@gmail.com", "amihaiemil");
    	
    	Language lang = Mockito.mock(Language.class);
    	Mockito.when(lang.response("denied.name.comment")).thenReturn(
    		"The repository's name must match the format owner.github.io or it must have a project website on branch gh-pages"
        );
    	
    	IndexSiteSteps iss = new IndexSiteSteps.IndexSiteStepsBuilder(
            com,
            com.issue().repo().json(),
            lang,
            Mockito.mock(Logger.class)
        )
    	.repoForkCheck(rfc)
    	.authorOwnerCheck(aoc)
    	.repoNameCheck(rnc)
    	.ghPagesBranchCheck(ghc)
        .build();
    	assertTrue(iss.perform());
    	String deniedMessage = com.issue().comments().iterate().iterator().next().json().getString("body");
    	assertTrue(
    	    deniedMessage.contains(
                "The repository's name must match the format owner.github.io or it must have a project website on branch gh-pages"
    		)
        );
    }

    /**
     * After checks pass and repo is indexed, a confirmation mail is sent to the commander.
     */
    @Test
    public void confirmationMailSent() throws Exception {
    	IndexSite is = Mockito.mock(IndexSite.class);
    	Mockito.when(is.perform()).thenReturn(true);
    	
    	RepoForkCheck rfc = Mockito.mock(RepoForkCheck.class);
    	Mockito.when(rfc.perform()).thenReturn(true);
    	
    	AuthorOwnerCheck aoc = Mockito.mock(AuthorOwnerCheck.class);
    	Mockito.when(aoc.perform()).thenReturn(true);

    	RepoNameCheck rnc = Mockito.mock(RepoNameCheck.class);
    	Mockito.when(rnc.perform()).thenReturn(true);

    	GhPagesBranchCheck ghc = Mockito.mock(GhPagesBranchCheck.class);
    	Mockito.when(ghc.perform()).thenReturn(false);
    	
    	Language lang = Mockito.mock(Language.class);
    	Mockito.when(lang.response("index.confirmation.email")).thenReturn(
    		"Your Github Pages repository (or gh-pages branch from) has been indexed"
        );
    	
    	Command com = this.mockCommand("amihaiemil", "amihaiemil@gmail.com", "amihaiemil");
    	IndexSiteSteps iss = Mockito.spy(
    	    new IndexSiteSteps.IndexSiteStepsBuilder(
    		    com, com.issue().repo().json(), lang, Mockito.mock(Logger.class)
            )
    	    .repoForkCheck(rfc)
    	    .authorOwnerCheck(aoc)
    	    .repoNameCheck(rnc)
    	    .ghPagesBranchCheck(ghc)
    	    .starRepo(Mockito.mock(StarRepo.class))
            .build()
        );
    	String repoName = com.issue().repo().json().getString("name");
    	
    	Mockito.when(iss.indexSiteStep("amihaiemil", repoName, false)).thenReturn(is);
    	
    	
    	String bind = "localhost";
		int port = this.port();
		GreenMail server = this.smtpServer(bind, port);
        server.start();
        
        System.setProperty("charles.smtp.username","mihai");
        System.setProperty("charles.smtp.password","");
        System.setProperty("charles.smtp.host","localhost");
        System.setProperty("charles.smtp.port", String.valueOf(port));
        
        try {
    	    assertTrue(iss.perform());
    	    String expectedSubject = "Repo " + repoName + " successfully indexed";
    	    final MimeMessage[] messages = server.getReceivedMessages();
            assertTrue(messages.length == 1);
            for (final Message msg : messages) {
                assertTrue(msg.getFrom()[0].toString().contains("mihai"));
                assertTrue(msg.getSubject().equals(expectedSubject));
            }
        } finally {
        	server.stop();
        }
    }
    
    /**
	 * Mock a command for the unit tests.
	 * @param author Author of the command.
	 * @param repoOwner Repository owner.
	 * @param authorEmail Command author's email.
	 * @return Command mock.
	 * @throws IOException If something goes wrong.
	 */
	private Command mockCommand(String author, String authorEmail, String repoSowner) throws IOException {
		MkGithub gh = new MkGithub(repoSowner);
		Issue issue = gh.randomRepo().issues().create("title", "body");
		Command command = Mockito.mock(Command.class);
		Mockito.when(command.authorLogin()).thenReturn(author);
		Mockito.when(command.authorEmail()).thenReturn(authorEmail);
		Mockito.when(command.issue()).thenReturn(issue);
		Mockito.when(command.json()).thenReturn(
		    Json.createObjectBuilder().add("body", "@charlesmike index pls").build()
        );
		return command;
	}
	
	/**
     * Mock a smtp server.
     * @return GreenMail smtp server.
     * @throws IOException If something goes wrong.
     */
    private GreenMail smtpServer(String bind, int port) throws IOException {
        return new GreenMail(
            new ServerSetup(
            	port, bind,
                ServerSetup.PROTOCOL_SMTP
            )
        );
	}
	
	/**
     * Find a free port.
     * @return A free port.
     * @throws IOException If something goes wrong.
     */
    private int port() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @After
    public void clean() {
    	System.clearProperty("charles.smtp.username");
        System.clearProperty("charles.smtp.password");
        System.clearProperty("charles.smtp.host");
        System.clearProperty("charles.smtp.port");
    }
}
