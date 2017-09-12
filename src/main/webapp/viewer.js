var BpmnJS = window.BpmnJS;
var viewer = new BpmnJS({container: document.querySelector('#js-canvas'), height: 650});
function load(diagramXML) {
    viewer.importXML(diagramXML, function (err) {
            if (err) {
                console.log('error rendering', err);
                return;
            }
            var eventBus = viewer.get('eventBus');
            var events = [
                'element.click'
            ];
            events.forEach(function (event) {
                eventBus.on(event, function (e) {
                    updateFields([{name: 'element', value: e.element.id}]);
                });
            });
            var canvas = viewer.get('canvas').zoom('fit-viewport');
        }
    );
}