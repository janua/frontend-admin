curl([
	'models/network-front',
	'Knockout',
	'Config',
    'Common',
    'TagEntry',
    'AutoComplete',
    'TagSearch',
    'ItemSearch'
]).then(function(
	NetworkFront,
	Knockout,
	Config,
    Common,
    TagEntry,
    AutoComplete,
    TagSearch,
    ItemSearch
) {
 	var networkFront = new NetworkFront(frontConfig);
 	Knockout.applyBindings(networkFront, document.getElementById('network-front-tool'));

 	var errorAlert = $(
 		'<div class="alert alert-error">'
        	+ '<button type="button" class="close" data-dismiss="alert">×</button>'
        	+ '<h4>Error!</h4>'
        	+ '<p class="message">There are errors in the form</p>'
    	+ '</div>'
	);

	var successAlert = $(
 		'<div class="alert alert-success">'
        	+ '<button type="button" class="close" data-dismiss="alert">×</button>'
        	+ '<h4>Success!</h4>'
        	+ '<p class="message">Form successfully saved</p>'
    	+ '</div>'
	);

    $('#network-front-tool').submit(function(e) {
    	e.preventDefault();
    	var form = $(e.currentTarget);
    	if (form.find('.invalid').length) {
    		errorAlert.insertBefore(form);
    	} else {
    		Common.mediator.emitEvent('ui:networkfronttool:save');
    	}
    });

    $('#network-front-tool .typeahead').blur(function(e) {
    	if ($(e.currentTarget).val()) {
    		Common.mediator.emitEvent('ui:networkfronttool:tagid:selected', [{}, e.currentTarget]);
    	}
    });

    // can't use standard reset type, doesn't fire change event on form
    $('#network-front-tool #clear-frontend').click(function(e) {
    	Common.mediator.emitEvent('ui:networkfronttool:clear');
    });

    // success alert when saved
    Common.mediator.addListener('models:networkfront:save:success', function(networkFront) {
    	successAlert.insertBefore($('#network-front-tool'));
    });

    new TagSearch.init( { apiEndPoint: 'http://content.guardianapis.com/tags', config: Config } );
    new TagEntry.init( { nodeList: $('.typeahead') } );

});