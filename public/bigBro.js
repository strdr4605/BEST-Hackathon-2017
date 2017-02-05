
$(function() {
  var FADE_TIME = 150; // ms
  var TYPING_TIMER_LENGTH = 400; // ms
  var COLORS = [
    '#e21400', '#91580f', '#f8a700', '#f78b00',
    '#58dc00', '#287b00', '#a8f07a', '#4ae8c4',
    '#3b88eb', '#3824aa', '#a700ff', '#d300e7'
  ];

  // Initialize variables
  var $window = $(window);
  var $usernameInput = $('.usernameInput'); // Input for username
  var $messages = $('.messages'); // Messages area
  var $devices = $('.devices');
  var $cam1 = $('.cam1');
  var $cam2 = $('.cam2');
  var $cam3 = $('.cam3');
  var $inputMessage = $('.inputMessage'); // Input message input box

  var $loginPage = $('.login.page'); // The login page
  var $chatPage = $('.chat.page'); // The chatroom page
  $loginPage.hide()
  $chatPage.hide()

  // Prompt for setting a username
  var username;
  var mainClient;
  var client = 'browser';
  var connected = false;
  var typing = false;
  var lastTypingTime;
  // var $currentInput = $usernameInput.focus();
  var $currentInput;
  var socket = io();

  $(document).on('click', 'li', function () {
    mainClient = $(this).text()
    $('li').css('background-color', 'black');
    $(this).css('background-color', 'gray');
  })

  $cam1.hide();
  $cam2.hide();

  function addParticipantsMessage (data) {
    var message = '';
    if (data.numUsers === 1) {
      message += "there's 1 participant";
    } else {
      message += "there are " + data.numUsers + " participants";
    }
    data.mobileUsersList.forEach(el =>  log2(el));
    log(message);
  }

  // Sets the client's username
  function setUsername () {
    // username = cleanInput($usernameInput.val().trim());
    username = Date.now() + ''

    // If the username is valid
    if (username) {
      $loginPage.fadeOut();
      $chatPage.show();
      $loginPage.off('click');
      $currentInput = $inputMessage.focus();

      // Tell the server your username
      socket.emit('add user', username, client);
    }
  }

  // Sends a chat message
  function sendMessage () {
    var message = $inputMessage.val();
    // Prevent markup from being injected into the message
    message = cleanInput(message);
    // if there is a non-empty message and a socket connection
    if (message && connected) {
      $inputMessage.val('');
      addChatMessage({
        username: username,
        message: message
      });
      // tell server to execute 'new message' and send along one parameter
      socket.emit('new message', message);
    }
  }

  // Log a message
  function log2 (message, options) {
    var $el = $('<li>').addClass('log list-group-item').text(message);
    addMessageElement2($el, options);
    console.log(message)
  }

  function log (message, options) {
    var $el = $('<li>').addClass('log list-group-item').text(message);
    addMessageElement($el, options);
    console.log(message)
  }

  // Adds the visual chat message to the message list
  function addChatMessage (data, options) {
    // Don't fade the message in if there is an 'X was typing'
    var $typingMessages = getTypingMessages(data);
    options = options || {};
    if ($typingMessages.length !== 0) {
      options.fade = false;
      $typingMessages.remove();
    }

    var $usernameDiv = $('<span class="username"/>')
      .text(data.username)
      .css('color', 'black');
    var $messageBodyDiv = $('<span class="messageBody">')
      .text(data.message);

    var typingClass = data.typing ? 'typing' : '';
    var $messageDiv = $('<li class="message"/>')
      .data('username', data.username)
      .addClass(typingClass)
      .append($usernameDiv, $messageBodyDiv);

    addMessageElement($messageDiv, options);
  }

  // Adds the visual chat typing message
  function addChatTyping (data) {
    data.typing = true;
    data.message = 'is typing';
    addChatMessage(data);
  }

  // Removes the visual chat typing message
  function removeChatTyping (data) {
    getTypingMessages(data).fadeOut(function () {
      $(this).remove();
    });
  }

  // Adds a message element to the messages and scrolls to the bottom
  // el - The element to add as a message
  // options.fade - If the element should fade-in (default = true)
  // options.prepend - If the element should prepend
  //   all other messages (default = false)
  function addMessageElement (el, options) {
    var $el = $(el);

    // Setup default options
    if (!options) {
      options = {};
    }
    if (typeof options.fade === 'undefined') {
      options.fade = true;
    }
    if (typeof options.prepend === 'undefined') {
      options.prepend = false;
    }

    // Apply options
    if (options.fade) {
      $el.hide().fadeIn(FADE_TIME);
    }
    if (options.prepend) {
      $messages.prepend($el);
    } else {
      $messages.append($el);
    }
    $messages[0].scrollTop = $messages[0].scrollHeight;
  }

  function addMessageElement2 (el, options) {
    var $el = $(el);

    // Setup default options
    if (!options) {
      options = {};
    }
    if (typeof options.fade === 'undefined') {
      options.fade = true;
    }
    if (typeof options.prepend === 'undefined') {
      options.prepend = false;
    }

    // Apply options
    if (options.fade) {
      $el.hide().fadeIn(FADE_TIME);
    }
    if (options.prepend) {
      $devices.prepend($el);
    } else {
      $devices.append($el);
    }
    $devices[0].scrollTop = $devices[0].scrollHeight;
  }

  // Prevents input from having injected markup
  function cleanInput (input) {
    return $('<div/>').text(input).text();
  }

  // Updates the typing event
  function updateTyping () {
    if (connected) {
      if (!typing) {
        typing = true;
        socket.emit('typing');
      }
      lastTypingTime = (new Date()).getTime();

      setTimeout(function () {
        var typingTimer = (new Date()).getTime();
        var timeDiff = typingTimer - lastTypingTime;
        if (timeDiff >= TYPING_TIMER_LENGTH && typing) {
          socket.emit('stop typing');
          typing = false;
        }
      }, TYPING_TIMER_LENGTH);
    }
  }

  // Gets the 'X is typing' messages of a user
  function getTypingMessages (data) {
    return $('.typing.message').filter(function (i) {
      return $(this).data('username') === data.username;
    });
  }

  // Gets the color of a username through our hash function
  function getUsernameColor (username) {
    // Compute hash code
    var hash = 7;
    for (var i = 0; i < username.length; i++) {
       hash = username.charCodeAt(i) + (hash << 5) - hash;
    }
    // Calculate color
    var index = Math.abs(hash % COLORS.length);
    return COLORS[index];
  }

  // Keyboard events

  $window.keydown(function (event) {
    // Auto-focus the current input when a key is typed
    if (!(event.ctrlKey || event.metaKey || event.altKey)) {
      $currentInput.focus();
    }
    // When the client hits ENTER on their keyboard
    if (event.which === 13) {
      if (username) {
        sendMessage();
        socket.emit('stop typing');
        typing = false;
      } else {
        setUsername();
      }
    }
  });

  $inputMessage.on('input', function() {
    updateTyping();
  });

  // Click events

  // Focus input when clicking anywhere on login page
  $loginPage.click(function () {
    $currentInput.focus();
  });

  // Focus input when clicking on the message input's border
  $inputMessage.click(function () {
    $inputMessage.focus();
  });

  // Socket events

  // Whenever the server emits 'login', log the login message
  socket.on('login', function (data) {
    connected = true;
    // Display the welcome message
    var message = "Welcome to Socket.IO Chat – ";
    log(message, {
      prepend: true
    });
    addParticipantsMessage(data);
  });

  // Whenever the server emits 'new message', update the chat body
  socket.on('new message', function (data) {
    addChatMessage(data);
    console.log(mainClient,data.username,data.message)
    if(mainClient == data.username) {
      switch(data.message) {
        case '1':$cam1.show(); $cam2.hide(); $cam3.hide(); $('#a1').css("background-color","red"); $('#a2, #a3, #a4, #a5, #a6').css("background-color","black"); break;
        case '2':$cam1.show(); $cam2.hide(); $cam3.hide(); $('#a2').css("background-color","red"); $('#a1, #a3, #a4, #a5, #a6').css("background-color","black"); break;
        case '3':$cam1.hide(); $cam2.show(); $cam3.hide(); $('#a3').css("background-color","red"); $('#a2, #a1, #a4, #a5, #a6').css("background-color","black"); break;
        case '4':$cam1.hide(); $cam2.show(); $cam3.hide(); $('#a4').css("background-color","red"); $('#a2, #a3, #a1, #a5, #a6').css("background-color","black"); break;
        case '5':$cam1.hide(); $cam2.hide(); $cam3.show(); $('#a5').css("background-color","red"); $('#a2, #a3, #a4, #a1, #a6').css("background-color","black"); break;
        case '6':$cam1.hide(); $cam2.hide(); $cam3.show(); $('#a6').css("background-color","red"); $('#a2, #a3, #a4, #a5, #a1').css("background-color","black"); break;
      }
    }
    //   if(data.message == 1 || data.message == 2){
    //     $cam1.show(); $cam2.hide(); $cam3.hide();
    //   } else if(data.message == 3 || data.message == 4){
    //     $cam1.hide(); $cam2.show(); $cam3.hide();
    //   } else if(data.message == 5 || data.message == 6){
    //     $cam1.hide(); $cam2.hide(); $cam3.show();
    //   }
    // }
  });

  // Whenever the server emits 'user joined', log it in the chat body
  socket.on('user joined', function (data) {
    $('.devices').empty()
    log(data.username + ' joined');
    addParticipantsMessage(data);
  });

  // Whenever the server emits 'user left', log it in the chat body
  socket.on('user left', function (data) {
    log(data.username + ' left');
    $('li:contains('+ data.username +')').remove();
    removeChatTyping(data);
  });

  // Whenever the server emits 'typing', show the typing message
  socket.on('typing', function (data) {
    addChatTyping(data);
  });

  // Whenever the server emits 'stop typing', kill the typing message
  socket.on('stop typing', function (data) {
    removeChatTyping(data);
  });

  socket.on('disconnect', function () {
    log('you have been disconnected');
  });

  socket.on('reconnect', function () {
    log('you have been reconnected');
    if (username) {
      socket.emit('add user', username, client);
    }
  });

  socket.on('reconnect_error', function () {
    log('attempt to reconnect has failed');
  });

});
