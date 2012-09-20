curl(['models/trailblockViewModel']).then(function(TrailblockViewModel) {

	var trailblocks = {
		'us': new TrailblockViewModel,
		'uk': new TrailblockViewModel
	}

	for (edition in trailblocks) {
		var trailblock = trailblocks[edition];
		var editionConfig = frontConfig[edition];
        // add values from server
        if (editionConfig) {
        	for (prop in trailblock) {
            	trailblock[prop] = editionConfig[prop];
           	}
        }
        // apply bindings
        ko.applyBindings(trailblock, document.getElementById(edition + '-trailblocks'));
    }

        $('#network-front').submit(function(e) {
            e.preventDefault();

            // turn trailblock models into json
            var data = {
            	'us': ko.toJS(trailblocks.us),
            	'uk': ko.toJS(trailblocks.uk)
            };

            $.ajax({
                contentType: 'application/json',
                type: "POST",
                url: '/json/save',
                dataType: 'json',
                data: JSON.stringify(data)
            })
        });

});