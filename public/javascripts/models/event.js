define(['models/article', 'models/agent', 'models/place', 'Knockout', 'Config', 'Common', 'Reqwest'], function (Article, Agent, Place, ko, Config, Common, Reqwest) {

    // zero pad the date getters
    Date.prototype.getHoursPadded = function() {
        return ("0" + this.getHours()).slice(-2);
    };
    
    Date.prototype.getMinutesPadded = function() {
        return ("0" + this.getMinutes()).slice(-2);
    };

    var Event = function(opts) {

        var maxBumpedArticles = 2,
            importanceBumped  = 100,
            importanceDefault = 50,
            saveInterval = 100, // milliseconds
            bumped = [],
            deBounced,
            self = this;

        // Input values that get post processed
        this._humanDate  = ko.observable();
        this._prettyDate = ko.observable();
        this._prettyTime = ko.observable();

        // Event 'schema' poperties
        this.title      = ko.observable();
        this.importance = ko.observable();
        
        // Collections
        this.agents     = ko.observableArray(); // people, organisations etc.
        this.content    = ko.observableArray();
        this.places     = ko.observableArray(); // locations 

        this.startDate  = ko.computed({
            read: function() {
                return this._prettyDate() + 'T' + this._prettyTime() + ':00.000Z';
            },
            write: function(value) {
                var d = new Date(value);
                this._prettyDate(d.toISOString().match(/^\d{4}-\d{2}-\d{2}/)[0]);
                this._prettyTime(d.getHoursPadded() +':'+ d.getMinutesPadded());
                this._humanDate(humanized_time_span(d));
            },
            owner: this
        });

        // Administrative vars
        this._tentative   = ko.observable(!opts || !opts.id); // No id means it's a new un-persisted event,
        this._editing     = ko.observable(this._tentative()); // so mark as editable
        this._hidden      = ko.observable();

        this.init = function (o) {

            o = o || {};

            self.content.removeAll();
            (o.content || []).map(function(a){
                var cached = opts.articleCache ? opts.articleCache[a.id] : undefined;
                if (cached) {
                    cached.importance = a.importance; // updating the cached article with incoming update
                    cached.colour = a.colour;
                    a = cached;
                }
                self.content.push(new Article(a));
            });

            if (bumped.length === 0) {
                self.content().map(function(a){
                    if (a.importance() > importanceDefault) {
                        bumped.push(a.id());
                    }
                });
            }

            // populate agents
            self.agents.removeAll(); 
            (o.agents || []).map(function(a){
                self.agents.push(new Agent(a));
            });

            // populate places            
            self.places.removeAll(); 
            (o.places || []).map(function(p){
                self.places.push(new Place(p));
            });

            this.title(o.title || '');
            this.importance(o.importance || importanceDefault);

            if (o.startDate) {
                this.startDate(new Date(o.startDate)); // today
            } else {
                var d = new Date();
                d.setHours(0, 0, 0, 0); // TODO verify this is required
                this.startDate(d);
            }

            this._isValid = ko.computed(function () {
                return !!this.slugify(this.title()); // TODO validate
            }, this);

        }
        
        this.addArticle = function(article) {
            var included = _.some(self.content(), function(item){
                return item.id() === article.id();
            });
            if (!included) {
                self.content.unshift(article);
                self.backgroundSave();
            }
        };

        this.addArticleById = function(id) { // merge with addArticle
            id = self.urlPath(id);
            var included = _.some(self.content(), function(item){
                return item.id() === id;
            });
            if (!included) {
                self.content.unshift(new Article({id: id}));
                self.backgroundSave();
            }
        };

        this.removeArticle = function(article) {
            
            var result = window.confirm("Are you sure you want to DELETE this article?");
            if (!result) return;
            
            self.content.remove(article);
            self.backgroundSave();
        };

        this.decorateContent = function() {
            var apiUrl = "/api/proxy/search";
            // Find articles that aren't yet decorated with API data..
            var areRaw = _.filter(self.content(), function(a){
                return ! a.webTitle();
            });
            // and grab them from the API
            if(areRaw.length) {
                apiUrl += "?page-size=50&format=json&show-fields=all&show-tags=all&api-key=" + Config.apiKey;
                apiUrl += "&ids=" + areRaw.map(function(article){
                    return encodeURIComponent(article.id());
                }).join(',');

                new Reqwest({
                    url: apiUrl,
                    type: 'jsonp',
                    success: function(resp) {
                        if (resp.response && resp.response.results) {
                            resp = resp.response.results;
                            resp.map(function(ra){
                                var c = _.find(areRaw,function(a){
                                    return a.id() === ra.id;
                                });
                                c.webTitle(ra.webTitle);
                                c.webPublicationDate(ra.webPublicationDate);
                                opts.articleCache[ra.id] = ra;
                            });
                        }
                    },
                    error: function() {}
                });
            }
        };

        this.toggleEditing = function() {
            this._editing(!this._editing());
        };

        this.toggleEditParent = function() {
            this._editParent(!this._editParent());
        };

        this.bump = function() {
            if (self.importance() > importanceDefault) {
                self.importance(importanceDefault);
            } else {
                self.importance(importanceBumped);
            }
            self.backgroundSave();
        };

        this.bumpContent = function(item) {
            var id = item.id();
            if (_.contains(bumped, id)) {
                bumped = _.without(bumped, id);
            } else {
                bumped.unshift(id);
                bumped = bumped.slice(0,maxBumpedArticles);
            }
            // Now adjust the importance of all content items accordingly
            self.content().map(function(a){
                if (_.some(bumped, function(b){
                    return a.id() === b;
                })) {
                    a.importance(importanceBumped);
                } else {
                    a.importance(importanceDefault);
                }
            });
            self.backgroundSave();
        };
    
        this.setColour = function(item) {
            var id = item.id();
            self.content().forEach(function(i){
                if (i.id() === id) {
                    if (item.colour() > 2) {
                        i.colour(1)
                    }
                    else { 
                        i.colour(5)
                    }
                }
            }); 
            self.backgroundSave();
        }

        this.urlPath = function(url) {
            var a = document.createElement('a');
            a.href = url;
            a = a.pathname + a.search;
            a = a.indexOf('/') === 0 ? a.substr(1) : a;
            return a;
        };

        this.slugify = function (str) {
            str = str
                .replace(/(?:(?:^|\n)\s+|\s+(?:$|\n))/g,'')
                .toLowerCase()
                .replace(/[^\w]+/g, '-') // unfair on utf-8 IMHO
                .replace(/(^-|-$)/g, '');
            return str;
        };

        this.init(opts);
    };

    return Event;
});
