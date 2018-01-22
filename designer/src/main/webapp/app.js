var App = angular.module('Rvd', [
	'angularFileUpload',
	'ngRoute',
	'ngDragDrop',
	'ui.bootstrap',
	'ui.bootstrap.collapse',
	'ui.bootstrap.popover',
	'ui.sortable',
	'basicDragdrop',
	'pascalprecht.translate',
	'ngSanitize',
	'ngResource',
	'ngCookies',
	'ngIdle',
	'ui.router',
	'ngStorage',
	'angular-md5',
	'ngFileSaver'
]);

var rvdMod = App;

App.config(['$stateProvider','$urlRouterProvider', '$translateProvider', function ($stateProvider,$urlRouterProvider,$translateProvider) {
    $stateProvider.state('root',{
        resolve:{
            init: function (initializer) {
                //console.log('Initializing RVD');
                return initializer.init();
            }
        }
    });
    $stateProvider.state('root.public',{});
    $stateProvider.state('root.public.login',{
        url:"/login",
        views: {
            'container@': {
                templateUrl: 'templates/login.html',
                controller: 'loginCtrl'
            }
        }
    });
    $stateProvider.state('root.public.notready',{
        url:'/notready',
        views: {
            'container@': {templateUrl: 'templates/notready.html'}
        }
    });
    $stateProvider.state('root.rvd',{
        views: {
            'authmenu@': {
                templateUrl: 'templates/index-authmenu.html',
                //template: '<div>ROOT.RVD</div>',
                controller: 'authMenuCtrl'
            },
            'container@': {
                template: '<ui-view/>',
                controller: 'containerCtrl'
            }
        },
        resolve: {
            authorize: function (init, authentication) { // block on init ;-)
                return authentication.checkRvdAccess(); // pass required role here
            }
        }
    });
    $stateProvider.state('root.rvd.noapp', {
        url: '/noapp',
    	templateUrl : 'templates/noApp.html',
    	controller : 'noAppCtrl'
    });
    $stateProvider.state('root.rvd.forbidden', {
        url: '/forbidden',
    	templateUrl : 'templates/forbidden.html',
    	controller : 'forbiddenCtrl'
    });
    $stateProvider.state('root.rvd.designer', {
        // parameters following the '?' are optional (and follow the typical query syntax (beore the fragment) - i.e. #/designer/AP73926e7113fa4d95981aa96b76eca854=rvdCollectVerbDemo?firstTime=true
        url: '/designer/:applicationSid=:projectName?firstTime',
        views: {
            'container@': {
                templateUrl : 'templates/designer.html',
                controller : 'designerCtrl'

            },
            'mainmenu@': {
                templateUrl: 'templates/designer-mainmenu.html',
                controller: 'designerMainmenuCtrl'
            }
        },
        resolve: {
            project: function(designerService, $stateParams, $state,authorize) {
                return designerService.openProject($stateParams.applicationSid);
            },
            bundledWavs: function(designerService,authorize) {
                return designerService.getBundledWavs();
            },
            projectParameters: function(parametersResource, $stateParams, authorize) {
                return parametersResource.get({applicationSid: $stateParams.applicationSid});
            }
        }
    });
    $stateProvider.state('root.rvd.projectLog', {
        url: '/designer/:applicationSid=:projectName/log',
    	templateUrl : 'templates/projectLog.html',
    	controller : 'projectLogCtrl'
    });
    /*
    $stateProvider.state('root.rvd.packaging',{template:'<ui-view/>'}); // does nothing for now
    $stateProvider.state('root.rvd.packaging.details',{
        url: '/packaging/:applicationSid=:projectName',
        templateUrl : 'templates/packaging/form.html',
        controller : 'packagingCtrl',
        resolve: {
            rappWrap: function(RappService, $stateParams,authorize) {return RappService.getRapp($stateParams);},
            rvdSettingsResolver: function (rvdSettings,authorize) {return rvdSettings.refresh();} // not meant to return anything back. Just trigger the fetching of the settings
        }
    });
    $stateProvider.state('root.rvd.packaging.download', {
        url:'/packaging/:applicationSid=:projectName/download',
   		templateUrl : 'templates/packaging/download.html',
   		controller : 'packagingDownloadCtrl',
   		resolve: {
   			binaryInfo: function (RappService,$stateParams,authorize) { return RappService.getBinaryInfo($stateParams)}
   		}
    });
    */

    $urlRouterProvider.otherwise('/noapp');

    $translateProvider.useStaticFilesLoader({
        prefix: '/restcomm-rvd/languages/',
        suffix: '.json'
    });
    $translateProvider.useCookieStorage();
    $translateProvider.preferredLanguage('en-US');
}]);

angular.element(document).ready(['$http',function ($http) {
  // manually inject $q since it's not available
  var initInjector = angular.injector(["ng"]);
  var $q = initInjector.get("$q");

  var configPromise = $q.defer();
  $http.get("services/config").success(function (clientConfig) {
    angular.module('Rvd').factory('RvdConfiguration', function () {
        return {
            projectsRootPath: '/restcomm-rvd/services/projects',
            videoSupport: clientConfig.videoSupport,
            restcommBaseUrl: clientConfig.restcommBaseUrl || "",
            ussdSupport: clientConfig.ussdSupport,
            projectVersion: clientConfig.projectVersion
        }
    });
    configPromise.resolve(clientConfig);
  }).error(function () {
    configPromise.reject();
  });

  $q.all([configPromise.promise]).then(function (responses) {
    angular.bootstrap(document, ["Rvd"]);
  }, function () {
    console.log("Internal server error");
  });
}]);

// endof bootstrapping section



App.config(function(IdleProvider, KeepaliveProvider, TitleProvider) {
    // configure Idle settings
    IdleProvider.idle(3600); // one hour
    IdleProvider.timeout(15); // in seconds
    KeepaliveProvider.interval(300); // 300 sec - every five minutes
    TitleProvider.enabled(false); // it is enabled by default
})
.run(function(Idle){
    // start watching when the app runs. also starts the Keepalive service by default.
    Idle.watch();
})
.run(function($rootScope, $state) {
    // set stateName variable in the header e.g. 'login' etc.
    $rootScope.uiState = $state;
    $rootScope.$watch("uiState.current.name", function (newValue) {
        //console.log("uiState.current.name changed!");
        var match = /[^.]*$/.exec(newValue);
        $rootScope.stateName = match[0];
    });
});

App.factory( 'dragService', [function () {
	var dragInfo;
	var dragId = 0;
	var pDragActive = false;
	var serviceInstance = {
		newDrag: function (model) {
			dragId ++;
			pDragActive = true;
			if ( typeof(model) === 'object' )
				dragInfo = { id : dragId, model : model };
			else
				dragInfo = { id : dragId, class : model };

			return dragId;
		},
		popDrag:  function () {
			if ( pDragActive ) {
				var dragInfoCopy = angular.copy(dragInfo);
				pDragActive = false;
				return dragInfoCopy;
			}
		},
		dragActive: function () {
			return pDragActive;
		}

	};
	return serviceInstance;
}]);

/*
App.factory('protos', function () {
	var protoInstance = {
		nodes: {
				voice: {kind:'voice', name:'module', label:'Untitled module', steps:[], iface:{edited:false,editLabel:false}},
				ussd: {kind:'ussd', name:'module', label:'Untitled module', steps:[], iface:{edited:false,editLabel:false}},
				sms: {kind:'sms', name:'module', label:'Untitled module', steps:[], iface:{edited:false,editLabel:false}},
		},
	};
	return protoInstance;
});
*/


App.filter('excludeNode', function() {
    return function(items, exclude_named) {
        var result = [];
        items.forEach(function (item) {
            if (item.name !== exclude_named) {
                result.push(item);
            }
        });
        return result;
    }
});

